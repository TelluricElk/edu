package com.eduappml.ui.gb

import com.eduappml.ui.lr.LrLab
import com.eduappml.ui.lr.PricePoint

data class Stump(val threshold: Float, val leftValue: Float, val rightValue: Float) {
    fun predict(area: Float): Float = if (area <= threshold) leftValue else rightValue
}

object GbLab {
    // Переиспользуем тот же датасет "площадь -> цена", что и в линейной регрессии
    val trainSet get() = LrLab.trainSet
    val testSet get() = LrLab.testSet

    private fun fitStump(areas: List<Float>, residuals: List<Float>): Stump {
        val thresholds = areas.distinct().sorted()
        var bestSse = Float.MAX_VALUE
        var best = Stump(areas.average().toFloat(), 0f, 0f)

        for (t in thresholds) {
            val leftIdx = areas.indices.filter { areas[it] <= t }
            val rightIdx = areas.indices.filter { areas[it] > t }
            if (leftIdx.isEmpty() || rightIdx.isEmpty()) continue
            val leftMean = leftIdx.map { residuals[it] }.average().toFloat()
            val rightMean = rightIdx.map { residuals[it] }.average().toFloat()
            val sse = leftIdx.sumOf { ((residuals[it] - leftMean) * (residuals[it] - leftMean)).toDouble() } +
                rightIdx.sumOf { ((residuals[it] - rightMean) * (residuals[it] - rightMean)).toDouble() }
            if (sse < bestSse) {
                bestSse = sse.toFloat()
                best = Stump(t, leftMean, rightMean)
            }
        }
        return best
    }

    data class BoostingModel(val f0: Float, val trees: List<Stump>, val learningRate: Float, val trainMseHistory: List<Float>, val testMseHistory: List<Float>)

    /** Реальный градиентный бустинг для регрессии: остатки -> пенёк -> обновление, на каждой итерации. */
    fun train(nEstimators: Int, learningRate: Float): BoostingModel {
        val areas = trainSet.map { it.area }
        val prices = trainSet.map { it.price }
        val f0 = prices.average().toFloat()
        var trainPred = FloatArray(trainSet.size) { f0 }
        val trees = mutableListOf<Stump>()
        val trainHistory = mutableListOf<Float>()
        val testHistory = mutableListOf<Float>()

        repeat(nEstimators) {
            val residuals = trainPred.indices.map { prices[it] - trainPred[it] }
            val stump = fitStump(areas, residuals)
            trees.add(stump)
            trainPred = trainPred.indices.map { trainPred[it] + learningRate * stump.predict(areas[it]) }.toFloatArray()

            trainHistory.add(mse(trainSet, f0, trees, learningRate))
            testHistory.add(mse(testSet, f0, trees, learningRate))
        }
        return BoostingModel(f0, trees, learningRate, trainHistory, testHistory)
    }

    fun predict(model: BoostingModel, area: Float): Float {
        var pred = model.f0
        model.trees.forEach { pred += model.learningRate * it.predict(area) }
        return pred
    }

    fun mse(data: List<PricePoint>, f0: Float, trees: List<Stump>, learningRate: Float): Float {
        if (data.isEmpty()) return 0f
        return data.sumOf {
            var pred = f0
            trees.forEach { s -> pred += learningRate * s.predict(it.area) }
            val err = pred - it.price
            (err * err).toDouble()
        }.toFloat() / data.size
    }
}
