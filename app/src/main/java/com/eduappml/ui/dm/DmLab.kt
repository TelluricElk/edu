package com.eduappml.ui.dm

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

data class Point2D(val x: Float, val y: Float)

object DmLab {
    const val FEATURE_MAX = 10f

    /** Два "облака" данных — известное заранее распределение (смесь двух гауссиан). */
    val means: Array<FloatArray> = arrayOf(floatArrayOf(-3f, 0f), floatArrayOf(3f, 0f))
    const val BASE_VAR = 0.5f
    val weights: FloatArray = floatArrayOf(0.5f, 0.5f)

    const val SIGMA_MAX = 6f
    const val SIGMA_MIN = 0.05f

    /** Фиксированный набор "настоящих" точек — для отображения целевого распределения. */
    val realSample: List<Point2D> by lazy { sampleReal(Random(555), 120) }

    private fun gaussian(rnd: Random): Float {
        val u1 = rnd.nextFloat().coerceAtLeast(1e-6f)
        val u2 = rnd.nextFloat()
        return (sqrt(-2f * ln(u1)) * kotlin.math.cos(2f * Math.PI.toFloat() * u2))
    }

    private fun sampleReal(rnd: Random, n: Int): List<Point2D> = List(n) {
        val k = if (rnd.nextFloat() < weights[0]) 0 else 1
        val mean = means[k]
        Point2D(mean[0] + gaussian(rnd) * sqrt(BASE_VAR), mean[1] + gaussian(rnd) * sqrt(BASE_VAR))
    }

    /** Прямой процесс: точная формула зашумления, без единого обучаемого параметра. */
    fun forwardNoise(x0: Point2D, sigma: Float, rnd: Random): Point2D =
        Point2D(x0.x + gaussian(rnd) * sigma, x0.y + gaussian(rnd) * sigma)

    /** Точная score-функция для смеси гауссиан — формула, не аппроксимация нейросетью. */
    private fun scoreExact(x: FloatArray, sigma: Float): FloatArray {
        val variance = BASE_VAR + sigma * sigma
        val logProbs = FloatArray(means.size) { k ->
            val dx = x[0] - means[k][0]
            val dy = x[1] - means[k][1]
            val sqDist = dx * dx + dy * dy
            (-sqDist / (2f * variance) + ln(weights[k].toDouble())).toFloat()
        }
        val maxLog = logProbs.max()
        val probs = FloatArray(means.size) { k -> exp((logProbs[k] - maxLog).toDouble()).toFloat() }
        val sum = probs.sum()
        for (k in probs.indices) probs[k] /= sum

        val score = floatArrayOf(0f, 0f)
        for (k in means.indices) {
            score[0] += probs[k] * (means[k][0] - x[0]) / variance
            score[1] += probs[k] * (means[k][1] - x[1]) / variance
        }
        return score
    }

    /** Обратный процесс (детерминированный, probability flow): точный score на каждом шаге, без обучения. */
    fun reverseSample(nPoints: Int, nSteps: Int, seed: Int = 1): List<Point2D> {
        val rnd = Random(seed)
        val points = Array(nPoints) { floatArrayOf(gaussian(rnd) * SIGMA_MAX, gaussian(rnd) * SIGMA_MAX) }

        val sigmas = FloatArray(nSteps + 1) { i ->
            SIGMA_MAX * (SIGMA_MIN / SIGMA_MAX).toDouble().pow(i.toDouble() / nSteps).toFloat()
        }

        for (step in 0 until nSteps) {
            val sigmaT = sigmas[step]
            val sigmaNext = sigmas[step + 1]
            val coeff = sigmaT * sigmaT - sigmaNext * sigmaNext
            for (p in points) {
                val s = scoreExact(p, sigmaT)
                p[0] += coeff * s[0]
                p[1] += coeff * s[1]
            }
        }
        return points.map { Point2D(it[0], it[1]) }
    }

    /** Доля точек, оказавшихся достаточно близко к одному из двух центров — метрика качества сэмплирования. */
    fun convergenceRatio(samples: List<Point2D>, threshold: Float = 1.5f): Float {
        if (samples.isEmpty()) return 0f
        val close = samples.count { p ->
            means.any { m ->
                val dx = p.x - m[0]; val dy = p.y - m[1]
                sqrt(dx * dx + dy * dy) < threshold
            }
        }
        return close.toFloat() / samples.size
    }
}
