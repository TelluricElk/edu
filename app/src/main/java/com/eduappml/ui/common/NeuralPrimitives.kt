package com.eduappml.ui.common

import kotlin.random.Random

enum class Activation { RELU, LINEAR, SIGMOID }

/**
 * Один полносвязный слой с ручным прямым и обратным проходом — строительный
 * блок для маленьких нейросетей в интерактивах (AE, GAN и т.д.). Никакого
 * притворства: реальные веса, реальный градиент, реальное обновление.
 */
class Dense(nIn: Int, val nOut: Int, private val activation: Activation, seed: Int) {
    val w: Array<FloatArray> = run {
        val rnd = Random(seed)
        Array(nOut) { FloatArray(nIn) { (rnd.nextFloat() * 2f - 1f) * 0.5f } }
    }
    val b: FloatArray = FloatArray(nOut)

    private var lastX: FloatArray = FloatArray(0)
    private var lastZ: FloatArray = FloatArray(0)
    private var lastA: FloatArray = FloatArray(0)

    private fun sigmoid(z: Float): Float = (1.0 / (1.0 + kotlin.math.exp(-z.toDouble()))).toFloat()

    fun forward(x: FloatArray): FloatArray {
        val z = FloatArray(nOut) { i ->
            var sum = b[i]
            for (j in x.indices) sum += w[i][j] * x[j]
            sum
        }
        val a = when (activation) {
            Activation.RELU -> FloatArray(nOut) { i -> maxOf(0f, z[i]) }
            Activation.SIGMOID -> FloatArray(nOut) { i -> sigmoid(z[i]) }
            Activation.LINEAR -> z
        }
        lastX = x
        lastZ = z
        lastA = a
        return a
    }

    /** Накапливает градиенты по W и b в [accumW]/[accumB] и возвращает градиент, передаваемый дальше назад. */
    fun backward(gradA: FloatArray, accumW: Array<FloatArray>, accumB: FloatArray): FloatArray {
        val gradZ = when (activation) {
            Activation.RELU -> FloatArray(nOut) { i -> if (lastZ[i] > 0f) gradA[i] else 0f }
            Activation.SIGMOID -> FloatArray(nOut) { i -> gradA[i] * lastA[i] * (1f - lastA[i]) }
            Activation.LINEAR -> gradA
        }

        for (i in 0 until nOut) {
            for (j in lastX.indices) accumW[i][j] += gradZ[i] * lastX[j]
            accumB[i] += gradZ[i]
        }

        val gradX = FloatArray(lastX.size)
        for (j in lastX.indices) {
            var sum = 0f
            for (i in 0 until nOut) sum += w[i][j] * gradZ[i]
            gradX[j] = sum
        }
        return gradX
    }

    fun newAccumulators(): Pair<Array<FloatArray>, FloatArray> =
        Array(w.size) { FloatArray(w[0].size) } to FloatArray(b.size)

    fun apply(accumW: Array<FloatArray>, accumB: FloatArray, lr: Float, n: Int) {
        for (i in 0 until nOut) {
            for (j in accumW[i].indices) w[i][j] -= lr * accumW[i][j] / n
            b[i] -= lr * accumB[i] / n
        }
    }
}
