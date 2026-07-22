package com.eduappml.ui.rf

import com.eduappml.ui.dt.CreditPoint
import com.eduappml.ui.dt.DtCriterion
import com.eduappml.ui.dt.DtLab
import com.eduappml.ui.dt.DtNode
import kotlin.random.Random

object RfLab {
    // Переиспользуем тот же датасет "выдать кредит", что и в теме "Дерево решений",
    // чтобы наглядно сравнивать одно дерево и лес на одних и тех же данных.
    val trainSet get() = DtLab.trainSet
    val testSet get() = DtLab.testSet

    private fun bootstrapSample(data: List<CreditPoint>, seed: Int): List<CreditPoint> {
        val rnd = Random(seed)
        return List(data.size) { data[rnd.nextInt(data.size)] }
    }

    /** Реальный bagging: каждое дерево — на своей бутстрап-выборке. */
    fun trainForest(nTrees: Int, maxDepth: Int, criterion: DtCriterion = DtCriterion.GINI): List<DtNode> {
        return (0 until nTrees).map { i ->
            val bootstrap = bootstrapSample(trainSet, seed = 1000 + i)
            DtLab.buildTree(bootstrap, criterion, maxDepth, minSamplesSplit = 4)
        }
    }

    fun predictForest(trees: List<DtNode>, point: CreditPoint): Boolean {
        val approvals = trees.count { DtLab.predict(it, point) }
        return approvals >= trees.size - approvals
    }

    fun accuracy(trees: List<DtNode>, data: List<CreditPoint>): Float {
        if (data.isEmpty()) return 0f
        val correct = data.count { predictForest(trees, it) == it.approved }
        return correct.toFloat() / data.size
    }
}
