package com.eduappml.ui.nb

import kotlin.math.ln
import kotlin.math.PI
import kotlin.random.Random

data class WeatherPoint(val temperature: Float, val humidity: Float, val beach: Boolean)

data class ClassStats(val meanTemp: Float, val varTemp: Float, val meanHumidity: Float, val varHumidity: Float, val prior: Float)

object NbLab {
    const val TEMP_MIN = 10f
    const val TEMP_MAX = 35f
    const val HUMIDITY_MIN = 20f
    const val HUMIDITY_MAX = 95f

    val trainSet: List<WeatherPoint> by lazy { generate(seed = 42, n = 70) }
    val testSet: List<WeatherPoint> by lazy { generate(seed = 777, n = 25) }

    private fun generate(seed: Int, n: Int): List<WeatherPoint> {
        val rnd = Random(seed)
        return (0 until n).map {
            val temp = TEMP_MIN + rnd.nextFloat() * (TEMP_MAX - TEMP_MIN)
            val humidity = HUMIDITY_MIN + rnd.nextFloat() * (HUMIDITY_MAX - HUMIDITY_MIN)
            // Идут на пляж, если тепло и не слишком влажно (с шумом)
            val score = (temp - 22f) - 0.15f * (humidity - 55f) + (rnd.nextFloat() - 0.5f) * 8f
            WeatherPoint(temp, humidity, score > 0f)
        }
    }

    fun fitStats(data: List<WeatherPoint>): Map<Boolean, ClassStats> {
        return listOf(true, false).associateWith { cls ->
            val subset = data.filter { it.beach == cls }
            if (subset.isEmpty()) return@associateWith ClassStats(20f, 25f, 55f, 400f, 0.01f)
            val meanTemp = subset.map { it.temperature }.average().toFloat()
            val varTemp = subset.map { (it.temperature - meanTemp) * (it.temperature - meanTemp) }.average().toFloat() + 1e-3f
            val meanHum = subset.map { it.humidity }.average().toFloat()
            val varHum = subset.map { (it.humidity - meanHum) * (it.humidity - meanHum) }.average().toFloat() + 1e-3f
            ClassStats(meanTemp, varTemp, meanHum, varHum, subset.size.toFloat() / data.size)
        }
    }

    private fun gaussianLogProb(x: Float, mean: Float, variance: Float): Float {
        val safeVar = variance + 1e-6f
        return (-0.5f * ln((2f * PI.toFloat() * safeVar).toDouble()).toFloat()
            - (x - mean) * (x - mean) / (2f * safeVar))
    }

    fun classProbabilities(point: WeatherPoint, stats: Map<Boolean, ClassStats>): Map<Boolean, Float> {
        val logScores = stats.mapValues { (_, s) ->
            ln(s.prior.toDouble()).toFloat() +
                gaussianLogProb(point.temperature, s.meanTemp, s.varTemp) +
                gaussianLogProb(point.humidity, s.meanHumidity, s.varHumidity)
        }
        // нормализация через softmax для удобного отображения в процентах
        val maxLog = logScores.values.max()
        val expScores = logScores.mapValues { kotlin.math.exp((it.value - maxLog).toDouble()).toFloat() }
        val sum = expScores.values.sum()
        return expScores.mapValues { it.value / sum }
    }

    fun classify(point: WeatherPoint, stats: Map<Boolean, ClassStats>): Boolean =
        classProbabilities(point, stats).maxByOrNull { it.value }!!.key

    fun accuracy(stats: Map<Boolean, ClassStats>, data: List<WeatherPoint>): Float {
        if (data.isEmpty()) return 0f
        val correct = data.count { classify(it, stats) == it.beach }
        return correct.toFloat() / data.size
    }
}
