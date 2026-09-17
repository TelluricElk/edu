package com.eduappml.ui.logr

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Логистическая регрессия на задаче детектирования фишинга в почтовом шлюзе.
 *
 * Пять признаков письма, все приведены к диапазону [0, 1]:
 *   0 — spf      провал проверки SPF/DKIM (0 = пройдена, 1 = провалена)
 *   1 — age      возраст домена ссылки    (0 = зарегистрирован вчера, 1 = старый)
 *   2 — links    насыщенность ссылками
 *   3 — urgency  давление срочностью в теме и теле
 *   4 — attach   риск вложения
 *
 * ВАЖНО ПРО ВОСПРОИЗВОДИМОСТЬ. Корпус писем генерируется собственным
 * линейным конгруэнтным генератором [Lcg] с параметрами java.util.Random,
 * а не kotlin.random.Random. Это сделано намеренно: точно такой же генератор
 * приведён в разделе «Код» на Python, поэтому питоновский листинг из урока
 * порождает ПОБИТОВО тот же корпус и выдаёт ровно те числа, что пользователь
 * видит на экране. Если поменять генератор — разойдутся урок и приложение.
 *
 * Все расчёты ведутся в Double, а не Float: интерактив показывает веса с
 * точностью до третьего знака и сравнивает их с листингом в уроке, а на
 * Float за 400 эпох накапливается расхождение уже во втором знаке.
 *
 * Калибровка диапазонов слайдеров выполнена симуляцией на Python по сетке
 * (скорость обучения x эпохи x лямбда x вес класса); значения по умолчанию
 * ниже — рабочая точка, от которой имеет смысл отталкиваться.
 */
class MailSample(val x: DoubleArray, val label: Double)

object LogrLab {

    const val N_FEAT = 5

    val featureNames = listOf(
        "Провал SPF/DKIM",
        "Возраст домена",
        "Число ссылок",
        "Срочность в теме",
        "Риск вложения"
    )

    /** Короткие подписи для тесной раскладки графика весов. */
    val featureShort = listOf("SPF", "Домен", "Ссылки", "Срочн.", "Влож.")

    // --- значения по умолчанию, подтверждённые симуляцией ---
    const val DEFAULT_LR = 1.0
    const val DEFAULT_EPOCHS = 120
    const val DEFAULT_LAMBDA = 0.01
    const val DEFAULT_W_POS = 1.0
    const val DEFAULT_THRESHOLD = 0.5
    const val DEFAULT_COST_FN = 10.0

    const val LR_MIN = 0.05
    const val LR_MAX = 20.0
    const val EPOCHS_MIN = 1
    const val EPOCHS_MAX = 400
    const val LAMBDA_MIN = 0.0
    const val LAMBDA_MAX = 0.20
    const val W_POS_MIN = 1.0
    const val W_POS_MAX = 20.0
    const val THRESHOLD_MIN = 0.05
    const val THRESHOLD_MAX = 0.95
    const val COST_FN_MIN = 1.0
    const val COST_FN_MAX = 50.0

    private const val PHISH_RATE = 0.30

    val trainSet: List<MailSample> by lazy { generate(seed = 42L, n = 300) }
    val testSet: List<MailSample> by lazy { generate(seed = 777L, n = 200) }

    // ------------------------------------------------------------------
    // Генерация корпуса
    // ------------------------------------------------------------------

    /** LCG с константами java.util.Random. Начальное состояние НЕ скремблируется
     *  (в отличие от java.util.Random) — ровно так же устроен питоновский Lcg
     *  в разделе «Код». */
    private class Lcg(seed: Long) {
        private var s: Long = seed and 0xFFFFFFFFFFFFL

        fun nextDouble(): Double {
            s = (s * 0x5DEECE66DL + 0xBL) and 0xFFFFFFFFFFFFL
            return (s ushr 24).toDouble() / (1L shl 24).toDouble()
        }

        fun bern(p: Double): Double = if (nextDouble() < p) 1.0 else 0.0

        /** Приближение нормального распределения суммой шести равномерных
         *  (Ирвин-Холл). Дисперсия суммы равна 0.5, отсюда деление на sqrt(0.5).
         *  Дешевле Бокса-Мюллера и не тянет log/cos на каждый признак. */
        fun gauss(mu: Double, sd: Double): Double {
            var acc = 0.0
            for (i in 0 until 6) acc += nextDouble()
            return mu + sd * (acc - 3.0) / sqrt(0.5)
        }
    }

    private fun clamp01(v: Double): Double = if (v < 0.0) 0.0 else if (v > 1.0) 1.0 else v

    private fun generate(seed: Long, n: Int): List<MailSample> {
        val rnd = Lcg(seed)
        val out = ArrayList<MailSample>(n)
        for (i in 0 until n) {
            val isPhish = if (rnd.nextDouble() < PHISH_RATE) 1.0 else 0.0
            val x = if (isPhish >= 0.5) {
                doubleArrayOf(
                    rnd.bern(0.62),
                    clamp01(rnd.gauss(0.28, 0.20)),
                    clamp01(rnd.gauss(0.58, 0.22)),
                    clamp01(rnd.gauss(0.63, 0.22)),
                    rnd.bern(0.34)
                )
            } else {
                doubleArrayOf(
                    rnd.bern(0.16),
                    clamp01(rnd.gauss(0.68, 0.22)),
                    clamp01(rnd.gauss(0.34, 0.20)),
                    clamp01(rnd.gauss(0.34, 0.21)),
                    rnd.bern(0.08)
                )
            }
            out.add(MailSample(x, isPhish))
        }
        return out
    }

    // ------------------------------------------------------------------
    // Модель
    // ------------------------------------------------------------------

    fun sigmoid(z: Double): Double = when {
        z < -30.0 -> 1e-13
        z > 30.0 -> 1.0 - 1e-13
        else -> 1.0 / (1.0 + exp(-z))
    }

    class FitResult(
        val w: DoubleArray,
        val b: Double,
        val diverged: Boolean,
        val lossHistory: DoubleArray
    )

    /**
     * Полный батч-градиентный спуск по взвешенному log-loss с L2.
     *
     * [wPos] — вес класса «фишинг» в функции потерь. Меняет саму модель
     * (в отличие от порога, который применяется уже поверх вероятностей)
     * и сознательно портит калиброванность.
     *
     * ЗАЖИМ ЗАТУХАНИЯ. Множитель при весе равен (1 - lr * lambda) и обязан
     * оставаться положительным. При lr = 20 и lambda = 0.2 он равен -3:
     * веса меняют знак каждую эпоху и за десяток шагов уходят в бесконечность.
     * Пользователь при этом видит не урок о регуляризации, а мусор, поэтому
     * произведение ограничено сверху величиной 0.5. В рабочей области
     * параметров ограничение не срабатывает — проверено симуляцией по всей
     * сетке lr x lambda.
     */
    fun fit(lr: Double, epochs: Int, lambda: Double, wPos: Double): FitResult {
        val w = DoubleArray(N_FEAT)
        var b = 0.0
        val decay = min(lr * lambda, 0.5)
        val data = trainSet
        val history = DoubleArray(epochs)

        for (e in 0 until epochs) {
            val gw = DoubleArray(N_FEAT)
            var gb = 0.0
            var wsum = 0.0
            var loss = 0.0

            for (m in data) {
                var z = b
                for (j in 0 until N_FEAT) z += w[j] * m.x[j]
                val p = sigmoid(z)
                val cw = if (m.label >= 0.5) wPos else 1.0
                wsum += cw
                val err = cw * (p - m.label)
                for (j in 0 until N_FEAT) gw[j] += err * m.x[j]
                gb += err
                loss += -cw * (m.label * kotlin.math.ln(maxOf(p, 1e-13)) +
                        (1.0 - m.label) * kotlin.math.ln(maxOf(1.0 - p, 1e-13)))
            }

            for (j in 0 until N_FEAT) w[j] -= lr * (gw[j] / wsum) + decay * w[j]
            b -= lr * (gb / wsum)
            history[e] = loss / wsum

            var broken = b.isNaN()
            for (j in 0 until N_FEAT) if (w[j].isNaN() || abs(w[j]) > 1e4) broken = true
            if (broken) return FitResult(w, b, true, history.copyOf(e + 1))
        }
        return FitResult(w, b, false, history)
    }

    fun proba(x: DoubleArray, w: DoubleArray, b: Double): Double {
        var z = b
        for (j in 0 until N_FEAT) z += w[j] * x[j]
        return sigmoid(z)
    }

    /** Вероятности для всей выборки — считаются один раз и переиспользуются
     *  и гистограммой, и ROC, и перебором порога. */
    fun scoreAll(data: List<MailSample>, w: DoubleArray, b: Double): DoubleArray {
        val out = DoubleArray(data.size)
        for (i in data.indices) out[i] = proba(data[i].x, w, b)
        return out
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
        /** Доля ложных срабатываний среди легитимных писем. */
        val fpr: Double get() = if (fp + tn == 0) 0.0 else fp.toDouble() / (fp + tn)
    }

    fun confusion(data: List<MailSample>, scores: DoubleArray, threshold: Double): ConfusionMatrix {
        var tp = 0; var fp = 0; var tn = 0; var fn = 0
        for (i in data.indices) {
            val predPositive = scores[i] >= threshold
            val actualPositive = data[i].label >= 0.5
            when {
                predPositive && actualPositive -> tp++
                predPositive && !actualPositive -> fp++
                !predPositive && !actualPositive -> tn++
                else -> fn++
            }
        }
        return ConfusionMatrix(tp, fp, tn, fn)
    }

    /** Суммарный ущерб: ложная тревога стоит 1, пропуск — costFn. */
    fun cost(cm: ConfusionMatrix, costFn: Double): Double = cm.fp * 1.0 + cm.fn * costFn

    class ThresholdSearch(val threshold: Double, val cost: Double)

    /** Перебор порога с шагом 0.01 — то, что в уроке названо «эмпирически
     *  лучшим порогом» в противовес теоретическому 1/(1+C). */
    fun bestThreshold(data: List<MailSample>, scores: DoubleArray, costFn: Double): ThresholdSearch {
        var bestT = 0.5
        var bestC = Double.MAX_VALUE
        var t = THRESHOLD_MIN
        while (t <= THRESHOLD_MAX + 1e-9) {
            val c = cost(confusion(data, scores, t), costFn)
            if (c < bestC) { bestC = c; bestT = t }
            t += 0.01
        }
        return ThresholdSearch(bestT, bestC)
    }

    /** Теоретически оптимальный порог для откалиброванной модели. */
    fun theoreticalThreshold(costFn: Double): Double = 1.0 / (1.0 + costFn)

    /** Площадь под ROC-кривой, посчитанная трапециями по отсортированным
     *  оценкам. Не зависит от порога — удобная сводная характеристика
     *  качества самой модели. */
    fun auc(data: List<MailSample>, scores: DoubleArray): Double {
        val order = scores.indices.sortedByDescending { scores[it] }
        var pos = 0
        for (m in data) if (m.label >= 0.5) pos++
        val neg = data.size - pos
        if (pos == 0 || neg == 0) return 0.5
        var tp = 0; var fp = 0
        var prevTp = 0; var prevFp = 0
        var area = 0.0
        for (idx in order) {
            if (data[idx].label >= 0.5) tp++ else fp++
            area += (fp - prevFp).toDouble() * (tp + prevTp).toDouble() / 2.0
            prevTp = tp; prevFp = fp
        }
        return area / (pos.toDouble() * neg.toDouble())
    }

    /** Точки ROC-кривой (fpr, tpr) по сетке порогов — для отрисовки. */
    fun rocCurve(data: List<MailSample>, scores: DoubleArray, steps: Int = 60): List<Pair<Double, Double>> {
        val pts = ArrayList<Pair<Double, Double>>(steps + 1)
        for (k in 0..steps) {
            val t = 1.0 - k.toDouble() / steps
            val cm = confusion(data, scores, t)
            pts.add(cm.fpr to cm.recall)
        }
        return pts
    }

    /** Гистограммы предсказанных вероятностей отдельно по классам. */
    class ProbaHistogram(val legit: IntArray, val phish: IntArray, val maxCount: Int)

    fun probaHistogram(data: List<MailSample>, scores: DoubleArray, bins: Int = 24): ProbaHistogram {
        val legit = IntArray(bins)
        val phish = IntArray(bins)
        for (i in data.indices) {
            var k = (scores[i] * bins).toInt()
            if (k < 0) k = 0
            if (k >= bins) k = bins - 1
            if (data[i].label >= 0.5) phish[k]++ else legit[k]++
        }
        var mx = 1
        for (v in legit) if (v > mx) mx = v
        for (v in phish) if (v > mx) mx = v
        return ProbaHistogram(legit, phish, mx)
    }

    /** Доля фишинга в выборке — база для разговора о том, чего стоит accuracy. */
    fun baseRate(data: List<MailSample>): Double {
        var pos = 0
        for (m in data) if (m.label >= 0.5) pos++
        return pos.toDouble() / data.size
    }
}
