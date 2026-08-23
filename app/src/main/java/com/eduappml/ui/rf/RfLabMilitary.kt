package com.eduappml.ui.rf

import com.eduappml.ui.dt.CandidatePoint
import com.eduappml.ui.dt.DtCriterion
import com.eduappml.ui.dt.DtLabMilitary
import com.eduappml.ui.dt.DtNodeMilitary
import kotlin.random.Random

/**
 * Военный вариант "эталонной задачи" случайного леса: переиспользует тот
 * же датасет "зачисление кандидата", что и военная версия дерева решений
 * ([DtLabMilitary]) — так же, как оригинальный [RfLab] переиспользует
 * [DtLab][com.eduappml.ui.dt.DtLab]. Сам [RfLab] остаётся нетронутым и
 * больше нигде не используется.
 */
object RfLabMilitary {
    val trainSet get() = DtLabMilitary.trainSet
    val testSet get() = DtLabMilitary.testSet

    private fun bootstrapSample(data: List<CandidatePoint>, seed: Int): List<CandidatePoint> {
        val rnd = Random(seed)
        return List(data.size) { data[rnd.nextInt(data.size)] }
    }

    /** Реальный bagging: каждое дерево — на своей бутстрап-выборке. */
    fun trainForest(nTrees: Int, maxDepth: Int, criterion: DtCriterion = DtCriterion.GINI): List<DtNodeMilitary> {
        return (0 until nTrees).map { i ->
            val bootstrap = bootstrapSample(trainSet, seed = 1000 + i)
            DtLabMilitary.buildTree(bootstrap, criterion, maxDepth, minSamplesSplit = 4)
        }
    }

    fun predictForest(trees: List<DtNodeMilitary>, point: CandidatePoint): Boolean {
        val approvals = trees.count { DtLabMilitary.predict(it, point) }
        return approvals >= trees.size - approvals
    }

    fun accuracy(trees: List<DtNodeMilitary>, data: List<CandidatePoint>): Float {
        if (data.isEmpty()) return 0f
        val correct = data.count { predictForest(trees, it) == it.admitted }
        return correct.toFloat() / data.size
    }
}
