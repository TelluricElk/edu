package com.eduappml.ui.ae

import com.eduappml.ui.common.Activation
import com.eduappml.ui.common.Dense
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Военный вариант "эталонной задачи" автокодировщика: сжатие координат
 * (X, Y) засечки радара, зафиксированной на дуге патрулирования БПЛА, до
 * одного числа. Полный структурный аналог [AeLab] (который остаётся
 * нетронутым и больше нигде не используется) — только с военным
 * прикладным контекстом вместо примера с абстрактной дугой. Переиспользует
 * тот же [Dense]/[Activation] из общего пакета `common`, что и оригинал —
 * эти классы предметно-нейтральны и не требуют военной версии.
 */
data class RadarPoint(val x: Float, val y: Float)

object AeLabMilitary {
    const val FEATURE_MAX = 10f

    val trainSet: List<RadarPoint> by lazy { generate(seed = 42, n = 60) }
    val testSet: List<RadarPoint> by lazy { generate(seed = 777, n = 20) }

    private fun generate(seed: Int, n: Int): List<RadarPoint> {
        val rnd = Random(seed)
        return List(n) {
            val theta = 0.3f + rnd.nextFloat() * 2.5f
            val x = 5f + 4f * cos(theta) + (rnd.nextFloat() - 0.5f) * 0.3f
            val y = 5f + 4f * sin(theta) + (rnd.nextFloat() - 0.5f) * 0.3f
            RadarPoint(x, y)
        }
    }

    private fun norm(p: RadarPoint): FloatArray = floatArrayOf(p.x / FEATURE_MAX * 2f - 1f, p.y / FEATURE_MAX * 2f - 1f)
    private fun denorm(v: FloatArray): RadarPoint = RadarPoint((v[0] + 1f) / 2f * FEATURE_MAX, (v[1] + 1f) / 2f * FEATURE_MAX)

    class Autoencoder(bottleneck: Int, hidden: Int, seed: Int = 7) {
        val enc1 = Dense(2, hidden, Activation.RELU, seed + 1)
        val enc2 = Dense(hidden, bottleneck, Activation.LINEAR, seed + 2)
        val dec1 = Dense(bottleneck, hidden, Activation.RELU, seed + 3)
        val dec2 = Dense(hidden, 2, Activation.LINEAR, seed + 4)
        private val layers = listOf(enc1, enc2, dec1, dec2)

        fun forwardNorm(xNorm: FloatArray): Pair<FloatArray, FloatArray> {
            val z = enc2.forward(enc1.forward(xNorm))
            val xHat = dec2.forward(dec1.forward(z))
            return z to xHat
        }

        fun reconstruct(p: RadarPoint): RadarPoint = denorm(forwardNorm(norm(p)).second)

        fun trainStep(data: List<RadarPoint>, lr: Float) {
            val accums = layers.associateWith { it.newAccumulators() }
            data.forEach { p ->
                val xNorm = norm(p)
                val (_, xHat) = forwardNorm(xNorm)
                var grad = FloatArray(2) { i -> 2f * (xHat[i] - xNorm[i]) }
                for (layer in layers.reversed()) {
                    val (aw, ab) = accums.getValue(layer)
                    grad = layer.backward(grad, aw, ab)
                }
            }
            layers.forEach { layer ->
                val (aw, ab) = accums.getValue(layer)
                layer.apply(aw, ab, lr, data.size)
            }
        }
    }

    fun train(bottleneck: Int, lr: Float, epochs: Int, hidden: Int = 6): Autoencoder {
        val ae = Autoencoder(bottleneck, hidden, seed = 7)
        repeat(epochs) { ae.trainStep(trainSet, lr) }
        return ae
    }

    fun mse(ae: Autoencoder, data: List<RadarPoint>): Float {
        if (data.isEmpty()) return 0f
        var total = 0f
        data.forEach { p ->
            val r = ae.reconstruct(p)
            val dx = r.x - p.x
            val dy = r.y - p.y
            total += dx * dx + dy * dy
        }
        return total / data.size
    }
}
