package com.eduappml.ui.gan

import com.eduappml.ui.common.Activation
import com.eduappml.ui.common.Dense
import kotlin.random.Random

data class Point2D(val x: Float, val y: Float)

object GanLab {
    const val FEATURE_MAX = 10f
    const val NOISE_DIM = 2
    private const val HIDDEN = 10

    private val TARGET_MEAN = 7f to 7f
    private const val TARGET_STD = 0.8f

    /** Фиксированный набор "настоящих" точек для отрисовки (не то же самое, что обучающая выборка — та сэмплируется на лету). */
    val realSample: List<Point2D> by lazy { sampleReal(Random(555), 120) }

    private fun sampleReal(rnd: Random, n: Int): List<Point2D> =
        List(n) {
            Point2D(
                (TARGET_MEAN.first + gaussian(rnd) * TARGET_STD).coerceIn(0f, FEATURE_MAX),
                (TARGET_MEAN.second + gaussian(rnd) * TARGET_STD).coerceIn(0f, FEATURE_MAX)
            )
        }

    // Box-Muller — генерация нормально распределённого случайного числа из равномерного
    private fun gaussian(rnd: Random): Float {
        val u1 = rnd.nextFloat().coerceAtLeast(1e-6f)
        val u2 = rnd.nextFloat()
        return (kotlin.math.sqrt(-2f * kotlin.math.ln(u1)) * kotlin.math.cos(2f * Math.PI.toFloat() * u2))
    }

    private fun norm(p: Point2D): FloatArray = floatArrayOf(p.x / FEATURE_MAX * 2f - 1f, p.y / FEATURE_MAX * 2f - 1f)
    private fun denorm(v: FloatArray): Point2D = Point2D((v[0] + 1f) / 2f * FEATURE_MAX, (v[1] + 1f) / 2f * FEATURE_MAX)

    class Generator(seed: Int) {
        val l1 = Dense(NOISE_DIM, HIDDEN, Activation.RELU, seed + 1)
        val l2 = Dense(HIDDEN, 2, Activation.LINEAR, seed + 2)
        fun forwardNorm(z: FloatArray): FloatArray = l2.forward(l1.forward(z))
        fun generate(z: FloatArray): Point2D = denorm(forwardNorm(z))
    }

    class Discriminator(seed: Int) {
        val l1 = Dense(2, HIDDEN, Activation.RELU, seed + 1)
        val l2 = Dense(HIDDEN, 1, Activation.SIGMOID, seed + 2)
        fun forwardNorm(xNorm: FloatArray): Float = l2.forward(l1.forward(xNorm))[0]
    }

    data class Gan(val g: Generator, val d: Discriminator)

    /** Реальное чередующееся обучение GAN: шаг дискриминатора, затем шаг генератора. Не имитация. */
    fun train(steps: Int, lrD: Float, lrG: Float, batch: Int = 24, seed: Int = 1): Gan {
        val g = Generator(seed)
        val d = Discriminator(seed + 100)
        val rnd = Random(seed + 2000)

        repeat(steps) {
            // --- шаг дискриминатора ---
            val (aD1w, aD1b) = d.l1.newAccumulators()
            val (aD2w, aD2b) = d.l2.newAccumulators()

            repeat(batch) {
                val real = sampleReal(rnd, 1)[0]
                val pReal = d.forwardNorm(norm(real))
                var grad = floatArrayOf(pReal - 1f)
                grad = d.l2.backward(grad, aD2w, aD2b)
                d.l1.backward(grad, aD1w, aD1b)

                val z = FloatArray(NOISE_DIM) { rnd.nextFloat() * 2f - 1f }
                val fakeNorm = g.forwardNorm(z)
                val pFake = d.forwardNorm(fakeNorm)
                var gradF = floatArrayOf(pFake - 0f)
                gradF = d.l2.backward(gradF, aD2w, aD2b)
                d.l1.backward(gradF, aD1w, aD1b)
            }
            d.l1.apply(aD1w, aD1b, lrD, batch * 2)
            d.l2.apply(aD2w, aD2b, lrD, batch * 2)

            // --- шаг генератора (дискриминатор не обновляется, но участвует в расчёте градиента) ---
            val (aG1w, aG1b) = g.l1.newAccumulators()
            val (aG2w, aG2b) = g.l2.newAccumulators()

            repeat(batch) {
                val z = FloatArray(NOISE_DIM) { rnd.nextFloat() * 2f - 1f }
                val fakeNorm = g.forwardNorm(z)
                val pFake = d.forwardNorm(fakeNorm)
                var grad = floatArrayOf(pFake - 1f)
                // считаем градиент сквозь дискриминатор, но НЕ обновляем его веса — используем "пустые" накопители
                val (dummyW1, dummyB1) = d.l1.newAccumulators()
                val (dummyW2, dummyB2) = d.l2.newAccumulators()
                grad = d.l2.backward(grad, dummyW2, dummyB2)
                grad = d.l1.backward(grad, dummyW1, dummyB1)
                grad = g.l2.backward(grad, aG2w, aG2b)
                g.l1.backward(grad, aG1w, aG1b)
            }
            g.l1.apply(aG1w, aG1b, lrG, batch)
            g.l2.apply(aG2w, aG2b, lrG, batch)
        }
        return Gan(g, d)
    }

    /** Свежая выборка сгенерированных точек текущим генератором — для отрисовки. */
    fun generatedSample(g: Generator, n: Int, seed: Int = 42): List<Point2D> {
        val rnd = Random(seed)
        return List(n) {
            val z = FloatArray(NOISE_DIM) { rnd.nextFloat() * 2f - 1f }
            g.generate(z)
        }
    }

    /** Средняя точность дискриминатора на равной смеси реальных и сгенерированных — ориентир "насколько легко их отличить" (0.5 = идеальный обман). */
    fun discriminatorAccuracy(gan: Gan, n: Int = 60): Float {
        val rnd = Random(9999)
        var correct = 0
        repeat(n) {
            val real = sampleReal(rnd, 1)[0]
            if (gan.d.forwardNorm(norm(real)) >= 0.5f) correct++
        }
        repeat(n) {
            val z = FloatArray(NOISE_DIM) { rnd.nextFloat() * 2f - 1f }
            val fakeNorm = gan.g.forwardNorm(z)
            if (gan.d.forwardNorm(fakeNorm) < 0.5f) correct++
        }
        return correct.toFloat() / (n * 2)
    }
}
