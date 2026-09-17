package com.eduappml.ui.lr

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Линейная регрессия на задаче прогноза мощности DDoS-атаки.
 *
 * Признак — размер ботнета в тысячах узлов, отклик — пиковая полоса в Гбит/с.
 * Истинная зависимость, из которой генерируется история: 0.42 Гбит/с на
 * тысячу узлов плюс 3.5 Гбит/с фона. Хорошо настроенное обучение обязано её
 * восстановить — это и есть эталонная проверка темы.
 *
 * Два учебных сюжета зашиты в данные намеренно.
 *
 * 1. МАСШТАБ ПРИЗНАКА. Узлы измеряются десятками тысяч, поэтому градиент по
 *    наклону на порядки крупнее градиента по свободному члену. Без
 *    нормализации спуск разваливается уже при скорости обучения 0.0008, с
 *    нормализацией держит 2.0 — запас примерно в три тысячи раз. Это
 *    ровно та ловушка, что описана в DEVELOPMENT_NOTES для исходной версии
 *    темы; здесь она превращена в переключатель, чтобы её можно было увидеть.
 *
 * 2. ВЫБРОСЫ. Атаки с амплификацией (усиление через чужие DNS/NTP-серверы)
 *    дают огромную полосу при малом ботнете, то есть лежат слева и высоко.
 *    Метод наименьших квадратов не робастен, и при росте их доли наклон
 *    падает с 0.42 до отрицательных значений. Доля задаётся слайдером.
 *
 * Генератор — тот же LCG с константами java.util.Random, что и в питоновском
 * листинге раздела «Код»: история атак получается побитово одинаковой.
 */
class AttackSample(val nodes: Double, val bandwidth: Double)

object LrLab {

    const val TRUE_K = 0.42
    const val TRUE_B = 3.5
    const val NODES_MIN = 4.0
    const val NODES_MAX = 120.0
    const val SAMPLE_COUNT = 160

    // --- значения по умолчанию, подтверждённые симуляцией ---
    const val DEFAULT_LR = 0.08
    const val DEFAULT_EPOCHS = 120
    const val DEFAULT_LAMBDA = 0.0
    const val DEFAULT_AMP_RATE = 0.0
    const val DEFAULT_NORMALIZE = true

    const val LR_MIN = 1e-5
    const val LR_MAX = 2.0
    const val EPOCHS_MIN = 1
    const val EPOCHS_MAX = 400
    const val LAMBDA_MIN = 0.0
    const val LAMBDA_MAX = 2.0
    const val AMP_MIN = 0.0
    const val AMP_MAX = 1.0

    /** Ботнет, для которого экран показывает прогноз «в понятных единицах». */
    const val FORECAST_NODES = 50.0

    // ------------------------------------------------------------------
    // Логарифмический слайдер скорости обучения
    // ------------------------------------------------------------------

    /**
     * Позиция слайдера 0..1 в скорость обучения и обратно.
     *
     * Диапазон охватывает пять с половиной порядков. На линейном слайдере вся
     * интересная область «без нормализации» (до 0.0008) уместилась бы в первые
     * четыре сотых доли его хода — то есть была бы недостижима пальцем.
     */
    fun sliderToLr(t: Float): Double {
        val lo = ln(LR_MIN) / ln(10.0)
        val hi = ln(LR_MAX) / ln(10.0)
        return 10.0.pow(lo + t.toDouble() * (hi - lo))
    }

    fun lrToSlider(lr: Double): Float {
        val lo = ln(LR_MIN) / ln(10.0)
        val hi = ln(LR_MAX) / ln(10.0)
        return ((ln(lr) / ln(10.0) - lo) / (hi - lo)).toFloat()
    }

    // ------------------------------------------------------------------
    // Данные
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

    /**
     * История атак. [ampRate] — доля атак с амплификацией; она возможна
     * только при небольшом ботнете (усилителем работают чужие серверы, своих
     * узлов много не нужно), поэтому выбросы садятся в левой части графика
     * и перекашивают прямую сильнее, чем если бы были разбросаны равномерно.
     */
    fun generate(ampRate: Double, seed: Long = 42L, n: Int = SAMPLE_COUNT): List<AttackSample> {
        val rnd = Lcg(seed)
        val out = ArrayList<AttackSample>(n)
        for (i in 0 until n) {
            val nodes = NODES_MIN + rnd.nextDouble() * (NODES_MAX - NODES_MIN)
            var bw = TRUE_K * nodes + TRUE_B + rnd.gauss(0.0, 3.2)
            if (nodes < 35.0 && rnd.nextDouble() < ampRate) {
                bw += 25.0 + rnd.nextDouble() * 45.0
            }
            out.add(AttackSample(nodes, maxOf(0.1, bw)))
        }
        return out
    }

    // ------------------------------------------------------------------
    // Обучение
    // ------------------------------------------------------------------

    class FitResult(
        val k: Double,
        val b: Double,
        val mu: Double,
        val sd: Double,
        val diverged: Boolean,
        val epochsDone: Int,
        val lossHistory: DoubleArray
    ) {
        /** Наклон в «Гбит/с на тысячу узлов» — то, что можно произнести вслух. */
        val slopeReal: Double get() = k / sd
        /** Свободный член в гигабитах. */
        val interceptReal: Double get() = b - k * mu / sd

        fun predict(nodes: Double): Double = k * ((nodes - mu) / sd) + b
    }

    fun fit(
        data: List<AttackSample>,
        lr: Double,
        epochs: Int,
        normalize: Boolean,
        lambda: Double
    ): FitResult {
        var mu = 0.0
        var sd = 1.0
        if (normalize) {
            var sum = 0.0
            for (p in data) sum += p.nodes
            mu = sum / data.size
            var acc = 0.0
            for (p in data) acc += (p.nodes - mu) * (p.nodes - mu)
            sd = sqrt(acc / data.size)
            if (sd < 1e-9) sd = 1.0
        }

        var k = 0.0
        var b = 0.0
        val n = data.size
        val history = DoubleArray(epochs)

        for (e in 0 until epochs) {
            var gk = 0.0
            var gb = 0.0
            var loss = 0.0
            for (p in data) {
                val xn = (p.nodes - mu) / sd
                val err = k * xn + b - p.bandwidth
                gk += err * xn
                gb += err
                loss += err * err
            }
            k -= lr * (gk / n + lambda * k)
            b -= lr * (gb / n)
            history[e] = loss / n
            if (k.isNaN() || b.isNaN() || abs(k) > 1e9) {
                return FitResult(k, b, mu, sd, true, e + 1, history.copyOf(e + 1))
            }
        }
        return FitResult(k, b, mu, sd, false, epochs, history)
    }

    // ------------------------------------------------------------------
    // Метрики
    // ------------------------------------------------------------------

    class Metrics(val mse: Double, val mae: Double, val r2: Double)

    fun metrics(data: List<AttackSample>, f: FitResult): Metrics {
        val n = data.size
        var ysum = 0.0
        for (p in data) ysum += p.bandwidth
        val ybar = ysum / n

        var sse = 0.0
        var sae = 0.0
        var sst = 0.0
        for (p in data) {
            val pred = f.predict(p.nodes)
            val r = pred - p.bandwidth
            sse += r * r
            sae += abs(r)
            sst += (p.bandwidth - ybar) * (p.bandwidth - ybar)
        }
        return Metrics(sse / n, sae / n, if (sst > 0.0) 1.0 - sse / sst else 0.0)
    }

    /** Максимальная полоса в выборке — нужна для масштаба вертикальной оси. */
    fun maxBandwidth(data: List<AttackSample>): Double {
        var mx = 1.0
        for (p in data) if (p.bandwidth > mx) mx = p.bandwidth
        return mx
    }

    /** Предел скорости обучения без нормализации — подписывается в интерактиве
     *  как справочная величина, найденная симуляцией. */
    const val UNNORMALIZED_LIMIT = 0.0008
}
