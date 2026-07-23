package com.eduappml.ui.fc

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.random.Random

data class XorPoint(val x1: Float, val x2: Float, val label: Float) // label: 0f или 1f

object FcLab {
    const val FEATURE_MIN = 0f
    const val FEATURE_MAX = 10f

    /** XOR-узор: точки по диагонали (нижний левый + верхний правый) — один класс. */
    val trainSet: List<XorPoint> by lazy { generate(seed = 42, perCluster = 20) }
    val testSet: List<XorPoint> by lazy { generate(seed = 777, perCluster = 10) }

    private fun generate(seed: Int, perCluster: Int): List<XorPoint> {
        val rnd = Random(seed)
        val corners = listOf(
            Triple(2.5f, 2.5f, 1f),
            Triple(7.5f, 7.5f, 1f),
            Triple(2.5f, 7.5f, 0f),
            Triple(7.5f, 2.5f, 0f)
        )
        val points = mutableListOf<XorPoint>()
        corners.forEach { (cx, cy, label) ->
            repeat(perCluster) {
                val x1 = (cx + (rnd.nextFloat() - 0.5f) * 2.2f).coerceIn(FEATURE_MIN, FEATURE_MAX)
                val x2 = (cy + (rnd.nextFloat() - 0.5f) * 2.2f).coerceIn(FEATURE_MIN, FEATURE_MAX)
                points.add(XorPoint(x1, x2, label))
            }
        }
        return points
    }

    private fun sigmoid(z: Float): Float = (1.0 / (1.0 + exp(-z.toDouble()))).toFloat()

    /** Нормализация признаков в [-1, 1] — стандартный приём для устойчивого обучения (см. также LrLab). */
    private fun norm(x1: Float, x2: Float): Pair<Float, Float> =
        (x1 / FEATURE_MAX * 2f - 1f) to (x2 / FEATURE_MAX * 2f - 1f)

    /**
     * Маленькая нейросеть: 2 входа -> [hiddenSize] нейронов ReLU -> 1 выход (сигмоида).
     * Реальный прямой и обратный проход, без библиотек.
     */
    class Network(val hiddenSize: Int, seed: Int = 7) {
        private val rnd = Random(seed)
        val w1 = Array(hiddenSize) { floatArrayOf((rnd.nextFloat() * 2f - 1f) * 0.8f, (rnd.nextFloat() * 2f - 1f) * 0.8f) }
        val b1 = FloatArray(hiddenSize)
        val w2 = FloatArray(hiddenSize) { (rnd.nextFloat() * 2f - 1f) * 0.8f }
        var b2 = 0f

        fun forward(x1raw: Float, x2raw: Float): Triple<FloatArray, FloatArray, Float> {
            val (x1, x2) = norm(x1raw, x2raw)
            val z1 = FloatArray(hiddenSize) { i -> w1[i][0] * x1 + w1[i][1] * x2 + b1[i] }
            val h = FloatArray(hiddenSize) { i -> max(0f, z1[i]) }
            var z2 = b2
            for (i in 0 until hiddenSize) z2 += h[i] * w2[i]
            return Triple(z1, h, sigmoid(z2))
        }

        fun predict(x1: Float, x2: Float): Float = forward(x1, x2).third

        /** Один шаг батч-градиентного спуска (обратное распространение ошибки) по всей выборке. */
        fun trainStep(data: List<XorPoint>, lr: Float) {
            val gW1 = Array(hiddenSize) { FloatArray(2) }
            val gB1 = FloatArray(hiddenSize)
            val gW2 = FloatArray(hiddenSize)
            var gB2 = 0f

            data.forEach { p ->
                val (x1, x2) = norm(p.x1, p.x2)
                val (z1, h, pred) = forward(p.x1, p.x2)
                val error = pred - p.label   // dL/dz2 (упрощение log-loss + сигмоиды)

                for (i in 0 until hiddenSize) gW2[i] += error * h[i]
                gB2 += error

                for (i in 0 until hiddenSize) {
                    val dh = w2[i] * error
                    val dz1 = if (z1[i] > 0f) dh else 0f   // производная ReLU
                    gW1[i][0] += dz1 * x1
                    gW1[i][1] += dz1 * x2
                    gB1[i] += dz1
                }
            }

            val n = data.size
            for (i in 0 until hiddenSize) {
                w1[i][0] -= lr * gW1[i][0] / n
                w1[i][1] -= lr * gW1[i][1] / n
                b1[i] -= lr * gB1[i] / n
                w2[i] -= lr * gW2[i] / n
            }
            b2 -= lr * gB2 / n
        }
    }

    data class TrainResult(val network: Network, val lossHistory: List<Float>)

    fun train(hiddenSize: Int, lr: Float, epochs: Int): TrainResult {
        val net = Network(hiddenSize, seed = 7)
        val history = mutableListOf<Float>()
        repeat(epochs) {
            net.trainStep(trainSet, lr)
            history.add(loss(net, trainSet))
        }
        return TrainResult(net, history)
    }

    fun loss(net: Network, data: List<XorPoint>): Float {
        if (data.isEmpty()) return 0f
        var sum = 0.0
        data.forEach { p ->
            val pred = net.predict(p.x1, p.x2).coerceIn(1e-6f, 1f - 1e-6f)
            sum += -(p.label * ln(pred.toDouble()) + (1 - p.label) * ln(1.0 - pred.toDouble()))
        }
        return (sum / data.size).toFloat()
    }

    fun accuracy(net: Network, data: List<XorPoint>): Float {
        if (data.isEmpty()) return 0f
        val correct = data.count { (net.predict(it.x1, it.x2) >= 0.5f) == (it.label >= 0.5f) }
        return correct.toFloat() / data.size
    }
}
