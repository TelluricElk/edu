package com.eduappml.ui.dt

import kotlin.math.ln
import kotlin.random.Random

data class CreditPoint(val age: Float, val income: Float, val approved: Boolean)

enum class DtCriterion(val label: String) { GINI("Джини"), ENTROPY("Энтропия") }

data class DtNode(
    val feature: Int? = null,       // 0 = age, 1 = income
    val threshold: Float? = null,
    val left: DtNode? = null,
    val right: DtNode? = null,
    val prediction: Boolean? = null
)

object DtLab {
    const val AGE_MIN = 18f
    const val AGE_MAX = 65f
    const val INCOME_MIN = 15f
    const val INCOME_MAX = 120f

    // n увеличен со 90 до 260: при 90 точках и min_samples_split=4 дерево упирается
    // в нехватку данных для дальнейшего дробления уже к глубине 4-5 — весь верхний
    // край слайдера глубины (6/7/8) давал идентичное дерево. При 260 точках реальная
    // глубина дерева растёт вместе со слайдером по всему диапазону 1..8.
    val trainSet: List<CreditPoint> by lazy { generate(seed = 42, n = 260) }
    val testSet: List<CreditPoint> by lazy { generate(seed = 777, n = 80) }

    private fun generate(seed: Int, n: Int): List<CreditPoint> {
        val rnd = Random(seed)
        return (0 until n).map {
            val age = AGE_MIN + rnd.nextFloat() * (AGE_MAX - AGE_MIN)
            val income = INCOME_MIN + rnd.nextFloat() * (INCOME_MAX - INCOME_MIN)
            // Одобряют, если доход достаточно высокий относительно возраста (простая нелинейная зона)
            val score = income - 0.6f * age + (rnd.nextFloat() - 0.5f) * 20f
            CreditPoint(age, income, score > 25f)
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

    private fun featureValue(p: CreditPoint, feature: Int) = if (feature == 0) p.age else p.income

    private fun bestSplit(data: List<CreditPoint>, criterion: DtCriterion): Pair<Int, Float>? {
        var bestGain = 0f
        var bestFeature: Int? = null
        var bestThreshold: Float? = null
        val parentImpurity = impurity(data.map { it.approved }, criterion)

        for (feature in 0..1) {
            val thresholds = data.map { featureValue(it, feature) }.distinct().sorted()
            for (t in thresholds) {
                val left = data.filter { featureValue(it, feature) <= t }
                val right = data.filter { featureValue(it, feature) > t }
                if (left.isEmpty() || right.isEmpty()) continue
                val n = data.size.toFloat()
                val weighted = (left.size / n) * impurity(left.map { it.approved }, criterion) +
                    (right.size / n) * impurity(right.map { it.approved }, criterion)
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

    private fun majority(data: List<CreditPoint>): Boolean =
        data.count { it.approved } >= data.size - data.count { it.approved }

    fun buildTree(data: List<CreditPoint>, criterion: DtCriterion, maxDepth: Int, minSamplesSplit: Int, depth: Int = 0): DtNode {
        if (data.isEmpty()) return DtNode(prediction = false)
        val allSame = data.all { it.approved == data.first().approved }
        if (allSame || depth >= maxDepth || data.size < minSamplesSplit) {
            return DtNode(prediction = majority(data))
        }
        val split = bestSplit(data, criterion) ?: return DtNode(prediction = majority(data))
        val (feature, threshold) = split
        val left = data.filter { featureValue(it, feature) <= threshold }
        val right = data.filter { featureValue(it, feature) > threshold }
        if (left.isEmpty() || right.isEmpty()) return DtNode(prediction = majority(data))
        return DtNode(
            feature = feature,
            threshold = threshold,
            left = buildTree(left, criterion, maxDepth, minSamplesSplit, depth + 1),
            right = buildTree(right, criterion, maxDepth, minSamplesSplit, depth + 1)
        )
    }

    fun predict(node: DtNode, point: CreditPoint): Boolean {
        node.prediction?.let { return it }
        val value = featureValue(point, node.feature!!)
        val branch = if (value <= node.threshold!!) node.left else node.right
        return predict(branch!!, point)
    }

    fun accuracy(tree: DtNode, data: List<CreditPoint>): Float {
        if (data.isEmpty()) return 0f
        val correct = data.count { predict(tree, it) == it.approved }
        return correct.toFloat() / data.size
    }

    fun depth(node: DtNode): Int {
        if (node.prediction != null) return 0
        return 1 + maxOf(depth(node.left!!), depth(node.right!!))
    }

    fun leafCount(node: DtNode): Int {
        if (node.prediction != null) return 1
        return leafCount(node.left!!) + leafCount(node.right!!)
    }
}
