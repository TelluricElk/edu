package com.eduappml.ui.svm

import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

data class SvmPoint(val x1: Float, val x2: Float, val label: Int) // label: -1 или +1

enum class SvmKernel(val label: String) {
    LINEAR("Линейное"),
    RBF("RBF")
}

object SvmLab {
    const val FEATURE_MIN = 0f
    const val FEATURE_MAX = 10f

    val trainSet: List<SvmPoint> by lazy { generate(seed = 42, perClass = 26) }
    val testSet: List<SvmPoint> by lazy { generate(seed = 777, perClass = 14) }

    private fun generate(seed: Int, perClass: Int): List<SvmPoint> {
        val rnd = Random(seed)
        val points = mutableListOf<SvmPoint>()
        repeat(perClass) {
            val x1 = (6.7f + rnd.nextFloat() * 3f - 1.5f).coerceIn(FEATURE_MIN, FEATURE_MAX)
            val x2 = (6.7f + rnd.nextFloat() * 3f - 1.5f).coerceIn(FEATURE_MIN, FEATURE_MAX)
            points.add(SvmPoint(x1, x2, 1))
        }
        repeat(perClass) {
            val x1 = (3.3f + rnd.nextFloat() * 3f - 1.5f).coerceIn(FEATURE_MIN, FEATURE_MAX)
            val x2 = (3.3f + rnd.nextFloat() * 3f - 1.5f).coerceIn(FEATURE_MIN, FEATURE_MAX)
            points.add(SvmPoint(x1, x2, -1))
        }
        return points
    }

    private fun kernelValue(a: SvmPoint, bx1: Float, bx2: Float, kernel: SvmKernel, gamma: Float): Float =
        when (kernel) {
            SvmKernel.LINEAR -> a.x1 * bx1 + a.x2 * bx2
            SvmKernel.RBF -> {
                val dx = a.x1 - bx1
                val dy = a.x2 - bx2
                exp(-gamma * (dx * dx + dy * dy).toDouble()).toFloat()
            }
        }

    data class Model(
        val alpha: FloatArray,
        val bias: Float,
        val kernel: SvmKernel,
        val gamma: Float,
        val lambda: Float,
        val iterations: Int
    )

    private fun rawScore(alpha: FloatArray, x1: Float, x2: Float, kernel: SvmKernel, gamma: Float, lambda: Float, iterations: Int): Float {
        var sum = 0f
        for (j in trainSet.indices) {
            if (alpha[j] != 0f) {
                sum += alpha[j] * trainSet[j].label * kernelValue(trainSet[j], x1, x2, kernel, gamma)
            }
        }
        return sum / (lambda * iterations)
    }

    /**
     * Обучение методом Pegasos (реальный стохастический субградиентный спуск,
     * см. impl.ru.md), обобщённым на ядра через накопление alpha-коэффициентов.
     *
     * После накопления alpha считается смещение (bias) — так же, как это делают
     * дуальные SVM-солверы: граница ставится ровно посередине между "сырыми"
     * оценками ближайших разноклассовых точек. Без смещения граница обязана
     * проходить через начало координат (0,0), а наши классы лежат в положительной
     * части координат — без bias модель физически не могла бы их разделить.
     */
    fun train(c: Float, kernel: SvmKernel, gamma: Float, iterations: Int): Model {
        val n = trainSet.size
        val lambda = 1f / (c * n)
        val alpha = FloatArray(n)
        val rnd = Random(2024)

        for (t in 1..iterations) {
            val i = rnd.nextInt(n)
            var sum = 0f
            for (j in 0 until n) {
                if (alpha[j] != 0f) {
                    sum += alpha[j] * trainSet[j].label * kernelValue(trainSet[j], trainSet[i].x1, trainSet[i].x2, kernel, gamma)
                }
            }
            sum /= (lambda * t)
            if (trainSet[i].label * sum < 1f) {
                alpha[i] += 1f
            }
        }

        val rawScores = trainSet.map { p -> rawScore(alpha, p.x1, p.x2, kernel, gamma, lambda, iterations) }
        val positiveScores = trainSet.indices.filter { trainSet[it].label == 1 }.map { rawScores[it] }
        val negativeScores = trainSet.indices.filter { trainSet[it].label == -1 }.map { rawScores[it] }
        val bias = if (positiveScores.isNotEmpty() && negativeScores.isNotEmpty()) {
            -((positiveScores.min() + negativeScores.max()) / 2f)
        } else 0f

        return Model(alpha, bias, kernel, gamma, lambda, iterations)
    }

    fun decisionValue(model: Model, x1: Float, x2: Float): Float =
        rawScore(model.alpha, x1, x2, model.kernel, model.gamma, model.lambda, model.iterations) + model.bias

    fun classify(model: Model, x1: Float, x2: Float): Int = if (decisionValue(model, x1, x2) >= 0f) 1 else -1

    fun accuracy(model: Model, data: List<SvmPoint>): Float {
        if (data.isEmpty()) return 0f
        val correct = data.count { classify(model, it.x1, it.x2) == it.label }
        return correct.toFloat() / data.size
    }

    fun supportVectorCount(model: Model): Int = model.alpha.count { it != 0f }

    /**
     * Явные веса (w1, w2, bias) — доступны только для линейного ядра, где
     * K(a,b) = a·b и итоговый классификатор буквально сводится к w·x + b.
     * Используется, чтобы нарисовать разделяющую линию и зазор в интерактиве.
     */
    fun linearWeights(model: Model): Triple<Float, Float, Float>? {
        if (model.kernel != SvmKernel.LINEAR) return null
        var w1 = 0f
        var w2 = 0f
        for (j in trainSet.indices) {
            if (model.alpha[j] != 0f) {
                w1 += model.alpha[j] * trainSet[j].label * trainSet[j].x1
                w2 += model.alpha[j] * trainSet[j].label * trainSet[j].x2
            }
        }
        w1 /= (model.lambda * model.iterations)
        w2 /= (model.lambda * model.iterations)
        return Triple(w1, w2, model.bias)
    }
}
