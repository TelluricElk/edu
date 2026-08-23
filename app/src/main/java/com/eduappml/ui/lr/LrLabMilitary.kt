package com.eduappml.ui.lr

import kotlin.random.Random

/**
 * Военный вариант "эталонной задачи" линейной регрессии: предсказание
 * расхода топлива автоколонны по дальности маршрута. Полный структурный
 * аналог [LrLab] (который остаётся нетронутым и больше нигде не
 * используется) — только с военным прикладным контекстом вместо примера
 * с ценой квартиры.
 */
data class FuelPoint(val distance: Float, val fuel: Float)

object LrLabMilitary {

    const val DISTANCE_MIN = 30f
    const val DISTANCE_MAX = 120f

    /** Обучающая выборка — фиксированный синтетический датасет (seed = 42). */
    val trainSet: List<FuelPoint> by lazy { generate(seed = 42, n = 40) }

    /** Отложенная (контрольная) выборка. */
    val testSet: List<FuelPoint> by lazy { generate(seed = 777, n = 15) }

    private const val TRUE_SLOPE = 1.9f
    private const val TRUE_INTERCEPT = 12f

    private fun generate(seed: Int, n: Int): List<FuelPoint> {
        val rnd = Random(seed)
        return (0 until n).map {
            val distance = DISTANCE_MIN + rnd.nextFloat() * (DISTANCE_MAX - DISTANCE_MIN)
            val noise = (rnd.nextFloat() * 2f - 1f) * 18f
            FuelPoint(distance, TRUE_SLOPE * distance + TRUE_INTERCEPT + noise)
        }
    }

    // --- Нормализация признаков ---
    // Дальность (~30..120) и расход топлива (~70..240) — числа большого масштаба,
    // поэтому "сырой" градиент получается огромным, и любая скорость обучения на
    // разумный взгляд диапазон почти сразу уводит модель в расхождение. Стандартное
    // решение — считать градиентный спуск в нормализованных координатах (среднее 0,
    // стандартное отклонение 1), а затем пересчитывать веса обратно в реальные
    // единицы для отображения.
    private val distanceMean: Float by lazy { trainSet.map { it.distance }.average().toFloat() }
    private val distanceStd: Float by lazy {
        val m = distanceMean
        kotlin.math.sqrt(trainSet.map { (it.distance - m) * (it.distance - m) }.average()).toFloat().coerceAtLeast(1e-3f)
    }
    private val fuelMean: Float by lazy { trainSet.map { it.fuel }.average().toFloat() }
    private val fuelStd: Float by lazy {
        val m = fuelMean
        kotlin.math.sqrt(trainSet.map { (it.fuel - m) * (it.fuel - m) }.average()).toFloat().coerceAtLeast(1e-3f)
    }

    private fun toRealWeights(a: Float, b: Float): Pair<Float, Float> {
        val w1 = a * fuelStd / distanceStd
        val w0 = fuelMean + fuelStd * b - w1 * distanceMean
        return w1 to w0
    }

    /** Один шаг градиентного спуска по MSE в нормализованных координатах. Настоящий расчёт, не имитация. */
    private fun gradientStepNormalized(data: List<FuelPoint>, a: Float, b: Float, lr: Float): Pair<Float, Float> {
        var gradA = 0f
        var gradB = 0f
        data.forEach { p ->
            val xn = (p.distance - distanceMean) / distanceStd
            val yn = (p.fuel - fuelMean) / fuelStd
            val error = (a * xn + b) - yn
            gradA += error * xn
            gradB += error
        }
        gradA = 2f * gradA / data.size
        gradB = 2f * gradB / data.size
        return (a - lr * gradA) to (b - lr * gradB)
    }

    data class FitResult(val w1: Float, val w0: Float, val diverged: Boolean, val mseHistory: List<Float>)

    /** Полный прогон градиентного спуска на [epochs] эпох. */
    fun fitGradientDescent(lr: Float, epochs: Int): FitResult {
        var a = 0f
        var b = 0f
        val history = mutableListOf<Float>()

        for (e in 0 until epochs) {
            val (na, nb) = gradientStepNormalized(trainSet, a, b, lr)
            a = na
            b = nb
            if (a.isNaN() || b.isNaN() || kotlin.math.abs(a) > 1e4f || kotlin.math.abs(b) > 1e4f) {
                return FitResult(0f, 0f, true, history)
            }
            val (w1, w0) = toRealWeights(a, b)
            history.add(mse(trainSet, w1, w0))
        }
        val (w1, w0) = toRealWeights(a, b)
        val finalMse = mse(trainSet, w1, w0)
        // "Разошлось" — это не только когда веса улетели в бесконечность, но и когда
        // итоговая ошибка стала заметно хуже, чем тривиальный прогноз "средний расход
        // по выборке" (baseline).
        val baselineMse = fuelStd * fuelStd
        val diverged = finalMse.isNaN() || finalMse > baselineMse * 3f
        return FitResult(w1, w0, diverged, history)
    }

    /** Точное решение методом наименьших квадратов (нормальное уравнение для одного признака). */
    fun closedFormFit(): Pair<Float, Float> {
        val n = trainSet.size
        val meanX = trainSet.sumOf { it.distance.toDouble() } / n
        val meanY = trainSet.sumOf { it.fuel.toDouble() } / n
        var num = 0.0
        var den = 0.0
        trainSet.forEach { p ->
            num += (p.distance - meanX) * (p.fuel - meanY)
            den += (p.distance - meanX) * (p.distance - meanX)
        }
        val w1 = (num / den).toFloat()
        val w0 = (meanY - w1 * meanX).toFloat()
        return w1 to w0
    }

    fun mse(data: List<FuelPoint>, w1: Float, w0: Float): Float {
        if (data.isEmpty()) return 0f
        return data.sumOf {
            val err = (w1 * it.distance + w0) - it.fuel
            (err * err).toDouble()
        }.toFloat() / data.size
    }

    fun r2(data: List<FuelPoint>, w1: Float, w0: Float): Float {
        if (data.isEmpty()) return 0f
        val meanY = data.sumOf { it.fuel.toDouble() } / data.size
        val ssRes = data.sumOf {
            val err = (w1 * it.distance + w0) - it.fuel
            (err * err).toDouble()
        }
        val ssTot = data.sumOf {
            val d = it.fuel - meanY
            (d * d)
        }
        if (ssTot == 0.0) return 0f
        return (1.0 - ssRes / ssTot).toFloat()
    }
}
