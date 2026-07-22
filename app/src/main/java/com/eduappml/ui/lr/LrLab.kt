package com.eduappml.ui.lr

import kotlin.random.Random

data class PricePoint(val area: Float, val price: Float)

object LrLab {

    const val AREA_MIN = 30f
    const val AREA_MAX = 120f

    /** Обучающая выборка — фиксированный синтетический датасет (seed = 42). */
    val trainSet: List<PricePoint> by lazy { generate(seed = 42, n = 40) }

    /** Отложенная (контрольная) выборка. */
    val testSet: List<PricePoint> by lazy { generate(seed = 777, n = 15) }

    private const val TRUE_SLOPE = 1.9f
    private const val TRUE_INTERCEPT = 12f

    private fun generate(seed: Int, n: Int): List<PricePoint> {
        val rnd = Random(seed)
        return (0 until n).map {
            val area = AREA_MIN + rnd.nextFloat() * (AREA_MAX - AREA_MIN)
            val noise = (rnd.nextFloat() * 2f - 1f) * 18f
            PricePoint(area, TRUE_SLOPE * area + TRUE_INTERCEPT + noise)
        }
    }

    // --- Нормализация признаков ---
    // Площадь (~30..120) и цена (~70..240) — числа большого масштаба, поэтому
    // "сырой" градиент получается огромным, и любая скорость обучения на разумный
    // взгляд диапазон почти сразу уводит модель в расхождение (это и была причина
    // того, что график казался нечувствительным к параметрам — он всегда либо
    // "ничего не делал", либо сразу "разваливался"). Стандартное решение —
    // считать градиентный спуск в нормализованных координатах (среднее 0,
    // стандартное отклонение 1), а затем пересчитывать веса обратно в реальные
    // единицы для отображения. Так скорость обучения ведёт себя предсказуемо
    // в удобном диапазоне (как у логистической регрессии), а не в диапазоне
    // тысячных долей процента.
    private val areaMean: Float by lazy { trainSet.map { it.area }.average().toFloat() }
    private val areaStd: Float by lazy {
        val m = areaMean
        kotlin.math.sqrt(trainSet.map { (it.area - m) * (it.area - m) }.average()).toFloat().coerceAtLeast(1e-3f)
    }
    private val priceMean: Float by lazy { trainSet.map { it.price }.average().toFloat() }
    private val priceStd: Float by lazy {
        val m = priceMean
        kotlin.math.sqrt(trainSet.map { (it.price - m) * (it.price - m) }.average()).toFloat().coerceAtLeast(1e-3f)
    }

    private fun toRealWeights(a: Float, b: Float): Pair<Float, Float> {
        val w1 = a * priceStd / areaStd
        val w0 = priceMean + priceStd * b - w1 * areaMean
        return w1 to w0
    }

    /** Один шаг градиентного спуска по MSE в нормализованных координатах. Настоящий расчёт, не имитация. */
    private fun gradientStepNormalized(data: List<PricePoint>, a: Float, b: Float, lr: Float): Pair<Float, Float> {
        var gradA = 0f
        var gradB = 0f
        data.forEach { p ->
            val xn = (p.area - areaMean) / areaStd
            val yn = (p.price - priceMean) / priceStd
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
        // итоговая ошибка стала заметно хуже, чем тривиальный прогноз "средняя цена
        // по выборке" (baseline). Проверка по сырой величине весов пропускала случаи
        // вроде lr, при котором модель попадает в неустойчивые колебания и даёт явно
        // негодный, но при этом не астрономически большой по модулю результат.
        val baselineMse = priceStd * priceStd
        val diverged = finalMse.isNaN() || finalMse > baselineMse * 3f
        return FitResult(w1, w0, diverged, history)
    }

    /** Точное решение методом наименьших квадратов (нормальное уравнение для одного признака). */
    fun closedFormFit(): Pair<Float, Float> {
        val n = trainSet.size
        val meanX = trainSet.sumOf { it.area.toDouble() } / n
        val meanY = trainSet.sumOf { it.price.toDouble() } / n
        var num = 0.0
        var den = 0.0
        trainSet.forEach { p ->
            num += (p.area - meanX) * (p.price - meanY)
            den += (p.area - meanX) * (p.area - meanX)
        }
        val w1 = (num / den).toFloat()
        val w0 = (meanY - w1 * meanX).toFloat()
        return w1 to w0
    }

    fun mse(data: List<PricePoint>, w1: Float, w0: Float): Float {
        if (data.isEmpty()) return 0f
        return data.sumOf {
            val err = (w1 * it.area + w0) - it.price
            (err * err).toDouble()
        }.toFloat() / data.size
    }

    fun r2(data: List<PricePoint>, w1: Float, w0: Float): Float {
        if (data.isEmpty()) return 0f
        val meanY = data.sumOf { it.price.toDouble() } / data.size
        val ssRes = data.sumOf {
            val err = (w1 * it.area + w0) - it.price
            (err * err).toDouble()
        }
        val ssTot = data.sumOf {
            val d = it.price - meanY
            (d * d)
        }
        if (ssTot == 0.0) return 0f
        return (1.0 - ssRes / ssTot).toFloat()
    }
}
