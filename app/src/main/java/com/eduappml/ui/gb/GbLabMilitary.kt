package com.eduappml.ui.gb

import com.eduappml.ui.lr.LrLabMilitary
import com.eduappml.ui.lr.FuelPoint

/**
 * Военный вариант "эталонной задачи" градиентного бустинга: переиспользует
 * тот же датасет "дальность → расход топлива", что и военная версия
 * линейной регрессии ([LrLabMilitary]) — так же, как оригинальный [GbLab]
 * переиспользует [LrLab][com.eduappml.ui.lr.LrLab]. Сам [GbLab] остаётся
 * нетронутым и больше нигде не используется.
 */
data class StumpMilitary(val threshold: Float, val leftValue: Float, val rightValue: Float) {
    fun predict(distance: Float): Float = if (distance <= threshold) leftValue else rightValue
}

object GbLabMilitary {
    val trainSet get() = LrLabMilitary.trainSet
    val testSet get() = LrLabMilitary.testSet

    private fun fitStump(distances: List<Float>, residuals: List<Float>): StumpMilitary {
        val thresholds = distances.distinct().sorted()
        var bestSse = Float.MAX_VALUE
        var best = StumpMilitary(distances.average().toFloat(), 0f, 0f)

        for (t in thresholds) {
            val leftIdx = distances.indices.filter { distances[it] <= t }
            val rightIdx = distances.indices.filter { distances[it] > t }
            if (leftIdx.isEmpty() || rightIdx.isEmpty()) continue
            val leftMean = leftIdx.map { residuals[it] }.average().toFloat()
            val rightMean = rightIdx.map { residuals[it] }.average().toFloat()
            val sse = leftIdx.sumOf { ((residuals[it] - leftMean) * (residuals[it] - leftMean)).toDouble() } +
                rightIdx.sumOf { ((residuals[it] - rightMean) * (residuals[it] - rightMean)).toDouble() }
            if (sse < bestSse) {
                bestSse = sse.toFloat()
                best = StumpMilitary(t, leftMean, rightMean)
            }
        }
        return best
    }

    data class BoostingModel(val f0: Float, val trees: List<StumpMilitary>, val learningRate: Float, val trainMseHistory: List<Float>, val testMseHistory: List<Float>)

    /** Реальный градиентный бустинг для регрессии: остатки -> пенёк -> обновление, на каждой итерации. */
    fun train(nEstimators: Int, learningRate: Float): BoostingModel {
        val distances = trainSet.map { it.distance }
        val fuelValues = trainSet.map { it.fuel }
        val f0 = fuelValues.average().toFloat()
        var trainPred = FloatArray(trainSet.size) { f0 }
        val trees = mutableListOf<StumpMilitary>()
        val trainHistory = mutableListOf<Float>()
        val testHistory = mutableListOf<Float>()

        repeat(nEstimators) {
            val residuals = trainPred.indices.map { fuelValues[it] - trainPred[it] }
            val stump = fitStump(distances, residuals)
            trees.add(stump)
            trainPred = trainPred.indices.map { trainPred[it] + learningRate * stump.predict(distances[it]) }.toFloatArray()

            trainHistory.add(mse(trainSet, f0, trees, learningRate))
            testHistory.add(mse(testSet, f0, trees, learningRate))
        }
        return BoostingModel(f0, trees, learningRate, trainHistory, testHistory)
    }

    fun predict(model: BoostingModel, distance: Float): Float {
        var pred = model.f0
        model.trees.forEach { pred += model.learningRate * it.predict(distance) }
        return pred
    }

    fun mse(data: List<FuelPoint>, f0: Float, trees: List<StumpMilitary>, learningRate: Float): Float {
        if (data.isEmpty()) return 0f
        return data.sumOf {
            var pred = f0
            trees.forEach { s -> pred += learningRate * s.predict(it.distance) }
            val err = pred - it.fuel
            (err * err).toDouble()
        }.toFloat() / data.size
    }
}
