package com.eduappml.ui.nb

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Гауссовский наивный Байес на задаче детектирования DGA-доменов —
 * имён, сгенерированных алгоритмом внутри вредоноса для связи с управляющим
 * сервером.
 *
 * Два признака, оба в диапазоне [0, 1]:
 *   0 — entropy  энтропия Шеннона имени домена
 *   1 — digits   доля цифр в имени
 *
 * ГЛАВНЫЙ УЧЕБНЫЙ СЮЖЕТ ТЕМЫ — наивное допущение о независимости признаков.
 * [generate] умеет создавать корпус с управляемой корреляцией признаков
 * ВНУТРИ класса (через общий скрытый фактор). При corr = 0 допущение
 * выполняется точно; при corr = 0.9 признаки почти дублируют друг друга,
 * модель считает одну улику дважды, и происходит главное:
 *
 *   corr=0.0  F1=0.844  точность=0.885  уверенность модели=0.873
 *   corr=0.9  F1=0.727  точность=0.805  уверенность модели=0.875
 *
 * То есть модель ошибается заметно чаще, НЕ становясь менее уверенной.
 * Ради этой пары чисел тема и написана.
 *
 * Все расчёты в Double. Генератор — тот же LCG с константами
 * java.util.Random, что и в питоновском листинге раздела «Код», поэтому
 * корпус доменов побитово совпадает с приведённым в уроке.
 */
class DomainSample(val x: DoubleArray, val label: Double)

/** Выученная статистика одного класса: по среднему и дисперсии на признак. */
class ClassStat(val means: DoubleArray, val variances: DoubleArray, val share: Double)

/** Вся модель целиком — восемь чисел плюс две доли классов. */
class NbModel(val legit: ClassStat?, val dga: ClassStat?) {
    val ready: Boolean get() = legit != null && dga != null
}

object NbLab {

    const val N_FEAT = 2

    val featureNames = listOf("Энтропия имени", "Доля цифр")

    // параметры классов: среднее и разброс по каждому признаку
    private val LEGIT = doubleArrayOf(0.48, 0.15, 0.10, 0.09)
    private val DGA = doubleArrayOf(0.70, 0.14, 0.26, 0.14)

    // --- значения по умолчанию, подтверждённые симуляцией ---
    const val DEFAULT_CORR = 0.0
    const val DEFAULT_TRAIN_SIZE = 300
    const val DEFAULT_PRIOR = 0.35
    const val DEFAULT_VAR_SMOOTHING = 0.0
    const val DEFAULT_THRESHOLD = 0.5

    const val CORR_MIN = 0.0
    const val CORR_MAX = 0.9
    const val TRAIN_MIN = 6
    const val TRAIN_MAX = 400
    const val PRIOR_MIN = 0.02
    const val PRIOR_MAX = 0.70
    const val SMOOTH_MIN = 0.0
    const val SMOOTH_MAX = 0.12
    const val THRESHOLD_MIN = 0.05
    const val THRESHOLD_MAX = 0.95

    /** Доля DGA в корпусе. В обучающем наборе она выровнена искусственно —
     *  в реальном DNS-потоке это доли процента, о чём сказано в уроке. */
    private const val DGA_RATE = 0.35

    const val TEST_SIZE = 200

    // ------------------------------------------------------------------
    // Генерация корпуса
    // ------------------------------------------------------------------

    private class Lcg(seed: Long) {
        private var s: Long = seed and 0xFFFFFFFFFFFFL
        fun nextDouble(): Double {
            s = (s * 0x5DEECE66DL + 0xBL) and 0xFFFFFFFFFFFFL
            return (s ushr 24).toDouble() / (1L shl 24).toDouble()
        }
        fun gauss(mu: Double, sd: Double): Double {
            var acc = 0.0
            for (i in 0 until 6) acc += nextDouble()
            return mu + sd * (acc - 3.0) / sqrt(0.5)
        }
    }

    private fun clamp01(v: Double): Double = if (v < 0.0) 0.0 else if (v > 1.0) 1.0 else v

    /**
     * [corr] — сила связи признаков внутри класса. Реализована через общий
     * скрытый фактор: доля corr дисперсии каждого признака приходится на
     * общий фактор, остальное — на собственный шум. При corr = 0 признаки
     * независимы, и наивное допущение выполняется точно.
     */
    fun generate(seed: Long, n: Int, corr: Double): List<DomainSample> {
        val rnd = Lcg(seed)
        val k = sqrt(corr)
        val rest = sqrt(1.0 - corr)
        val out = ArrayList<DomainSample>(n)
        for (i in 0 until n) {
            val isDga = if (rnd.nextDouble() < DGA_RATE) 1.0 else 0.0
            val p = if (isDga >= 0.5) DGA else LEGIT
            val shared = rnd.gauss(0.0, 1.0)
            val z1 = k * shared + rest * rnd.gauss(0.0, 1.0)
            val z2 = k * shared + rest * rnd.gauss(0.0, 1.0)
            out.add(
                DomainSample(
                    doubleArrayOf(clamp01(p[0] + p[1] * z1), clamp01(p[2] + p[3] * z2)),
                    isDga
                )
            )
        }
        return out
    }

    fun trainSet(corr: Double, size: Int): List<DomainSample> = generate(42L, size, corr)
    fun testSet(corr: Double): List<DomainSample> = generate(777L, TEST_SIZE, corr)

    // ------------------------------------------------------------------
    // Обучение: один проход, никаких итераций
    // ------------------------------------------------------------------

    private fun statFor(data: List<DomainSample>, dga: Boolean, varSmoothing: Double): ClassStat? {
        val pts = data.filter { (it.label >= 0.5) == dga }
        if (pts.size < 2) return null
        val means = DoubleArray(N_FEAT)
        val variances = DoubleArray(N_FEAT)
        for (j in 0 until N_FEAT) {
            var m = 0.0
            for (p in pts) m += p.x[j]
            m /= pts.size
            var v = 0.0
            for (p in pts) v += (p.x[j] - m) * (p.x[j] - m)
            v /= pts.size
            means[j] = m
            variances[j] = maxOf(v + varSmoothing, 1e-6)
        }
        return ClassStat(means, variances, pts.size.toDouble() / data.size)
    }

    fun fit(data: List<DomainSample>, varSmoothing: Double): NbModel =
        NbModel(statFor(data, false, varSmoothing), statFor(data, true, varSmoothing))

    // ------------------------------------------------------------------
    // Предсказание
    // ------------------------------------------------------------------

    fun logGauss(x: Double, mean: Double, variance: Double): Double =
        -0.5 * ln(2.0 * Math.PI * variance) - (x - mean) * (x - mean) / (2.0 * variance)

    /** Плотность нормального распределения — нужна для отрисовки кривых. */
    fun gaussDensity(x: Double, mean: Double, variance: Double): Double =
        exp(logGauss(x, mean, variance))

    /**
     * Апостериорная вероятность класса «DGA».
     *
     * [priorDga] приходит извне, а не из обучающей выборки: доля DGA в корпусе
     * выровнена искусственно, а базовая частота в потоке — другая. В SOC её
     * оценивают наблюдением, и это отдельный слайдер интерактива.
     */
    fun probaDga(x: DoubleArray, model: NbModel, priorDga: Double): Double {
        val legit = model.legit ?: return 0.5
        val dga = model.dga ?: return 0.5
        // НАИВНЫЙ ШАГ: логарифмы правдоподобий по признакам складываются,
        // то есть сами правдоподобия перемножаются как независимые улики
        var logDga = ln(maxOf(priorDga, 1e-12))
        var logLegit = ln(maxOf(1.0 - priorDga, 1e-12))
        for (j in 0 until N_FEAT) {
            logDga += logGauss(x[j], dga.means[j], dga.variances[j])
            logLegit += logGauss(x[j], legit.means[j], legit.variances[j])
        }
        val mx = maxOf(logDga, logLegit)
        val a = exp(logDga - mx)
        val b = exp(logLegit - mx)
        return a / (a + b)
    }

    /** Вклад одного признака в решение — разность логарифмов правдоподобий.
     *  Положительный тянет к «DGA», отрицательный — к «легитимно». */
    fun featureContribution(x: DoubleArray, model: NbModel, j: Int): Double {
        val legit = model.legit ?: return 0.0
        val dga = model.dga ?: return 0.0
        return logGauss(x[j], dga.means[j], dga.variances[j]) -
            logGauss(x[j], legit.means[j], legit.variances[j])
    }

    // ------------------------------------------------------------------
    // Метрики
    // ------------------------------------------------------------------

    class ConfusionMatrix(val tp: Int, val fp: Int, val tn: Int, val fn: Int) {
        val precision: Double get() = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
        val recall: Double get() = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
        val f1: Double
            get() {
                val p = precision
                val r = recall
                return if (p + r == 0.0) 0.0 else 2.0 * p * r / (p + r)
            }
        val accuracy: Double
            get() {
                val total = tp + fp + tn + fn
                return if (total == 0) 0.0 else (tp + tn).toDouble() / total
            }
    }

    fun evaluate(
        data: List<DomainSample>,
        model: NbModel,
        prior: Double,
        threshold: Double
    ): ConfusionMatrix {
        var tp = 0; var fp = 0; var tn = 0; var fn = 0
        for (s in data) {
            val predPositive = probaDga(s.x, model, prior) >= threshold
            val actualPositive = s.label >= 0.5
            when {
                predPositive && actualPositive -> tp++
                predPositive && !actualPositive -> fp++
                !predPositive && !actualPositive -> tn++
                else -> fn++
            }
        }
        return ConfusionMatrix(tp, fp, tn, fn)
    }

    /**
     * Средняя уверенность модели в собственном ответе. Смотреть на неё нужно
     * ВМЕСТЕ с F1: расхождение этих двух чисел и есть мера того, насколько
     * модели можно верить на слово.
     */
    fun meanConfidence(data: List<DomainSample>, model: NbModel, prior: Double): Double {
        if (data.isEmpty()) return 0.5
        var total = 0.0
        for (s in data) {
            val p = probaDga(s.x, model, prior)
            total += maxOf(p, 1.0 - p)
        }
        return total / data.size
    }

    /** Граница решения: для каждого столбца сетки ищем значение второго
     *  признака, где вероятность пересекает порог. null там, где пересечения
     *  нет вовсе. */
    fun decisionBoundary(
        model: NbModel,
        prior: Double,
        threshold: Double,
        steps: Int = 64
    ): List<Double?> {
        if (!model.ready) return List(steps + 1) { null }
        return (0..steps).map { i ->
            val x1 = i.toDouble() / steps
            var found: Double? = null
            var prev = probaDga(doubleArrayOf(x1, 0.0), model, prior)
            for (k in 1..steps) {
                val x2 = k.toDouble() / steps
                val cur = probaDga(doubleArrayOf(x1, x2), model, prior)
                if ((prev < threshold) != (cur < threshold)) {
                    val t = (threshold - prev) / (cur - prev)
                    found = (k - 1 + t) / steps
                    break
                }
                prev = cur
            }
            found
        }
    }
}
