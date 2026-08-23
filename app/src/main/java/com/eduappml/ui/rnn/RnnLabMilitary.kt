package com.eduappml.ui.rnn

import kotlin.math.exp
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Военный вариант "эталонной задачи" RNN: определение финального состояния
 * маяка-ответчика (включён/выключен), переключаемого последовательностью
 * кодовых импульсов. Полный структурный аналог [RnnLab] (который остаётся
 * нетронутым и больше нигде не используется) — только со сменой сцены
 * ("выключатель света" → "радиомаяк").
 */
data class BeaconSequence(val pulses: List<Float>, val label: Float) // label: 1f = включён, 0f = выключен

object RnnLabMilitary {
    const val HIDDEN = 4

    /** Датасет "маяк" генерируется заново под конкретную длину — длина сама по себе ключевой параметр темы. */
    fun trainSet(length: Int): List<BeaconSequence> = generate(seed = 42, n = 50, length = length)
    fun testSet(length: Int): List<BeaconSequence> = generate(seed = 777, n = 20, length = length)

    private fun generate(seed: Int, n: Int, length: Int): List<BeaconSequence> {
        val rnd = Random(seed)
        return List(n) {
            val pulses = List(length) { rnd.nextInt(2).toFloat() }
            var state = 0f
            pulses.forEach { s -> if (s > 0.5f) state = 1f - state }
            BeaconSequence(pulses, state)
        }
    }

    /** Простая (Elman) RNN-ячейка: один скалярный вход на шаг, скрытое состояние размера [HIDDEN]. */
    class Network(seed: Int = 3) {
        private val rnd = Random(seed)
        val wxh: FloatArray = FloatArray(HIDDEN) { (rnd.nextFloat() * 2f - 1f) * 0.5f }
        val whh: Array<FloatArray> = Array(HIDDEN) { FloatArray(HIDDEN) { (rnd.nextFloat() * 2f - 1f) * 0.5f } }
        val bh: FloatArray = FloatArray(HIDDEN)
        val why: FloatArray = FloatArray(HIDDEN) { (rnd.nextFloat() * 2f - 1f) * 0.5f }
        var by: Float = 0f

        /** Прямой проход. Возвращает всю историю скрытых состояний (нужна для BPTT) и итоговую вероятность. */
        fun forward(sequence: List<Float>): Pair<List<FloatArray>, Float> {
            var h = FloatArray(HIDDEN)
            val history = mutableListOf(h.copyOf())
            for (x in sequence) {
                val newH = FloatArray(HIDDEN) { i ->
                    var sum = wxh[i] * x + bh[i]
                    for (j in 0 until HIDDEN) sum += whh[i][j] * h[j]
                    tanh(sum)
                }
                h = newH
                history.add(h.copyOf())
            }
            var yLin = by
            for (i in 0 until HIDDEN) yLin += why[i] * h[i]
            val p = (1.0 / (1.0 + exp(-yLin.toDouble()))).toFloat()
            return history to p
        }

        fun predict(sequence: List<Float>): Float = forward(sequence).second

        /** Обратное распространение во времени (BPTT) — реальный, не показной расчёт. */
        fun trainStep(sequence: List<Float>, label: Float, lr: Float) {
            val (history, p) = forward(sequence)
            val T = sequence.size

            val gradP = p - label
            val gradWhy = FloatArray(HIDDEN) { i -> gradP * history[T][i] }
            val gradBy = gradP
            var gradHNext = FloatArray(HIDDEN) { i -> gradP * why[i] }

            val gradWxh = FloatArray(HIDDEN)
            val gradWhh = Array(HIDDEN) { FloatArray(HIDDEN) }
            val gradBh = FloatArray(HIDDEN)

            for (t in T - 1 downTo 0) {
                val hT = history[t + 1]
                val hPrev = history[t]
                val x = sequence[t]
                val dtanh = FloatArray(HIDDEN) { i -> (1f - hT[i] * hT[i]) * gradHNext[i] }

                for (i in 0 until HIDDEN) {
                    gradWxh[i] += dtanh[i] * x
                    gradBh[i] += dtanh[i]
                    for (j in 0 until HIDDEN) gradWhh[i][j] += dtanh[i] * hPrev[j]
                }

                val newGradHNext = FloatArray(HIDDEN) { j ->
                    var sum = 0f
                    for (i in 0 until HIDDEN) sum += whh[i][j] * dtanh[i]
                    sum
                }
                gradHNext = newGradHNext
            }

            for (i in 0 until HIDDEN) {
                wxh[i] -= lr * gradWxh[i]
                bh[i] -= lr * gradBh[i]
                why[i] -= lr * gradWhy[i]
                for (j in 0 until HIDDEN) whh[i][j] -= lr * gradWhh[i][j]
            }
            by -= lr * gradBy
        }
    }

    fun train(length: Int, lr: Float, epochs: Int, seed: Int = 3): Network {
        val net = Network(seed)
        val data = trainSet(length)
        repeat(epochs) {
            data.forEach { seq -> net.trainStep(seq.pulses, seq.label, lr) }
        }
        return net
    }

    fun accuracy(net: Network, data: List<BeaconSequence>): Float {
        if (data.isEmpty()) return 0f
        val correct = data.count { (net.predict(it.pulses) >= 0.5f) == (it.label >= 0.5f) }
        return correct.toFloat() / data.size
    }
}
