package com.eduappml.ui.nb

import kotlin.math.ln
import kotlin.math.PI
import kotlin.random.Random

/**
 * Военный вариант "эталонной задачи" наивного Байеса: предсказание, будут
 * ли занятия проведены в поле или в помещении, по температуре и влажности.
 * Полный структурный аналог [NbLab] (который остаётся нетронутым и больше
 * нигде не используется) — только с военным прикладным контекстом вместо
 * примера "пляж или дом".
 */
data class TrainingWeatherPoint(val temperature: Float, val humidity: Float, val fieldTraining: Boolean)

data class TrainingClassStats(val meanTemp: Float, val varTemp: Float, val meanHumidity: Float, val varHumidity: Float, val prior: Float)

object NbLabMilitary {
    const val TEMP_MIN = 10f
    const val TEMP_MAX = 35f
    const val HUMIDITY_MIN = 20f
    const val HUMIDITY_MAX = 95f

    val trainSet: List<TrainingWeatherPoint> by lazy { generate(seed = 42, n = 70) }
    val testSet: List<TrainingWeatherPoint> by lazy { generate(seed = 777, n = 25) }

    private fun generate(seed: Int, n: Int): List<TrainingWeatherPoint> {
        val rnd = Random(seed)
        return (0 until n).map {
            val temp = TEMP_MIN + rnd.nextFloat() * (TEMP_MAX - TEMP_MIN)
            val humidity = HUMIDITY_MIN + rnd.nextFloat() * (HUMIDITY_MAX - HUMIDITY_MIN)
            // Занятия проводят в поле, если тепло и не слишком влажно (с шумом)
            val score = (temp - 22f) - 0.15f * (humidity - 55f) + (rnd.nextFloat() - 0.5f) * 8f
            TrainingWeatherPoint(temp, humidity, score > 0f)
        }
    }

    fun fitStats(data: List<TrainingWeatherPoint>): Map<Boolean, TrainingClassStats> {
        return listOf(true, false).associateWith { cls ->
            val subset = data.filter { it.fieldTraining == cls }
            if (subset.isEmpty()) return@associateWith TrainingClassStats(20f, 25f, 55f, 400f, 0.01f)
            val meanTemp = subset.map { it.temperature }.average().toFloat()
            val varTemp = subset.map { (it.temperature - meanTemp) * (it.temperature - meanTemp) }.average().toFloat() + 1e-3f
            val meanHum = subset.map { it.humidity }.average().toFloat()
            val varHum = subset.map { (it.humidity - meanHum) * (it.humidity - meanHum) }.average().toFloat() + 1e-3f
            TrainingClassStats(meanTemp, varTemp, meanHum, varHum, subset.size.toFloat() / data.size)
        }
    }

    private fun gaussianLogProb(x: Float, mean: Float, variance: Float): Float {
        val safeVar = variance + 1e-6f
        return (-0.5f * ln((2f * PI.toFloat() * safeVar).toDouble()).toFloat()
            - (x - mean) * (x - mean) / (2f * safeVar))
    }

    fun classProbabilities(point: TrainingWeatherPoint, stats: Map<Boolean, TrainingClassStats>): Map<Boolean, Float> {
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

    fun classify(point: TrainingWeatherPoint, stats: Map<Boolean, TrainingClassStats>): Boolean =
        classProbabilities(point, stats).maxByOrNull { it.value }!!.key

    fun accuracy(stats: Map<Boolean, TrainingClassStats>, data: List<TrainingWeatherPoint>): Float {
        if (data.isEmpty()) return 0f
        val correct = data.count { classify(it, stats) == it.fieldTraining }
        return correct.toFloat() / data.size
    }
}
