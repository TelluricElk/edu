package com.eduappml.ui.dt

import kotlin.math.ln
import kotlin.random.Random

/**
 * Военный вариант "эталонной задачи" дерева решений: зачисление кандидата
 * на курсы подготовки резервистов по возрасту и баллу вступительных
 * испытаний. Полный структурный аналог [DtLab] (который остаётся
 * нетронутым и больше нигде не используется, включая RfLab.kt — при
 * переносе темы "Случайный лес" на военные рельсы её стоит переключить на
 * этот же [DtLabMilitary], как оригинал переиспользует [DtLab]).
 */
data class CandidatePoint(val age: Float, val examScore: Float, val admitted: Boolean)

data class DtNodeMilitary(
    val feature: Int? = null,       // 0 = age, 1 = examScore
    val threshold: Float? = null,
    val left: DtNodeMilitary? = null,
    val right: DtNodeMilitary? = null,
    val prediction: Boolean? = null
)

object DtLabMilitary {
    const val AGE_MIN = 18f
    const val AGE_MAX = 65f
    const val SCORE_MIN = 15f
    const val SCORE_MAX = 120f

    val trainSet: List<CandidatePoint> by lazy { generate(seed = 42, n = 260) }
    val testSet: List<CandidatePoint> by lazy { generate(seed = 777, n = 80) }

    private fun generate(seed: Int, n: Int): List<CandidatePoint> {
        val rnd = Random(seed)
        return (0 until n).map {
            val age = AGE_MIN + rnd.nextFloat() * (AGE_MAX - AGE_MIN)
            val score = SCORE_MIN + rnd.nextFloat() * (SCORE_MAX - SCORE_MIN)
            // Зачисляют, если балл достаточно высокий относительно возраста (простая нелинейная зона)
            val decisionScore = score - 0.6f * age + (rnd.nextFloat() - 0.5f) * 20f
            CandidatePoint(age, score, decisionScore > 25f)
        }
    }

    private fun impurity(labels: List<Boolean>, criterion: DtCriterion): Float {
        if (labels.isEmpty()) return 0f
        val p1 = labels.count { it }.toFloat() / labels.size
        val p0 = 1f - p1
        return when (criterion) {
            DtCriterion.GINI -> 1f - (p0 * p0 + p1 * p1)
            DtCriterion.ENTROPY -> {
                fun term(p: Float) = if (p <= 0f) 0f else -p * (ln(p.toDouble()) / ln(2.0)).toFloat()
                term(p0) + term(p1)
            }
        }
    }

    private fun featureValue(p: CandidatePoint, feature: Int) = if (feature == 0) p.age else p.examScore

    private fun bestSplit(data: List<CandidatePoint>, criterion: DtCriterion): Pair<Int, Float>? {
        var bestGain = 0f
        var bestFeature: Int? = null
        var bestThreshold: Float? = null
        val parentImpurity = impurity(data.map { it.admitted }, criterion)

        for (feature in 0..1) {
            val thresholds = data.map { featureValue(it, feature) }.distinct().sorted()
            for (t in thresholds) {
                val left = data.filter { featureValue(it, feature) <= t }
                val right = data.filter { featureValue(it, feature) > t }
                if (left.isEmpty() || right.isEmpty()) continue
                val n = data.size.toFloat()
                val weighted = (left.size / n) * impurity(left.map { it.admitted }, criterion) +
                    (right.size / n) * impurity(right.map { it.admitted }, criterion)
                val gain = parentImpurity - weighted
                if (gain > bestGain) {
                    bestGain = gain
                    bestFeature = feature
                    bestThreshold = t
                }
            }
        }
        return if (bestFeature != null && bestThreshold != null) bestFeature to bestThreshold else null
    }

    private fun majority(data: List<CandidatePoint>): Boolean =
        data.count { it.admitted } >= data.size - data.count { it.admitted }

    fun buildTree(data: List<CandidatePoint>, criterion: DtCriterion, maxDepth: Int, minSamplesSplit: Int, depth: Int = 0): DtNodeMilitary {
        if (data.isEmpty()) return DtNodeMilitary(prediction = false)
        val allSame = data.all { it.admitted == data.first().admitted }
        if (allSame || depth >= maxDepth || data.size < minSamplesSplit) {
            return DtNodeMilitary(prediction = majority(data))
        }
        val split = bestSplit(data, criterion) ?: return DtNodeMilitary(prediction = majority(data))
        val (feature, threshold) = split
        val left = data.filter { featureValue(it, feature) <= threshold }
        val right = data.filter { featureValue(it, feature) > threshold }
        if (left.isEmpty() || right.isEmpty()) return DtNodeMilitary(prediction = majority(data))
        return DtNodeMilitary(
            feature = feature,
            threshold = threshold,
            left = buildTree(left, criterion, maxDepth, minSamplesSplit, depth + 1),
            right = buildTree(right, criterion, maxDepth, minSamplesSplit, depth + 1)
        )
    }

    fun predict(node: DtNodeMilitary, point: CandidatePoint): Boolean {
        node.prediction?.let { return it }
        val value = featureValue(point, node.feature!!)
        val branch = if (value <= node.threshold!!) node.left else node.right
        return predict(branch!!, point)
    }

    fun accuracy(tree: DtNodeMilitary, data: List<CandidatePoint>): Float {
        if (data.isEmpty()) return 0f
        val correct = data.count { predict(tree, it) == it.admitted }
        return correct.toFloat() / data.size
    }

    fun depth(node: DtNodeMilitary): Int {
        if (node.prediction != null) return 0
        return 1 + maxOf(depth(node.left!!), depth(node.right!!))
    }

    fun leafCount(node: DtNodeMilitary): Int {
        if (node.prediction != null) return 1
        return leafCount(node.left!!) + leafCount(node.right!!)
    }
}
