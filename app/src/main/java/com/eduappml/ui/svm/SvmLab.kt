package com.eduappml.ui.svm

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Метод опорных векторов на задаче отделения аномального сетевого трафика
 * от легитимного.
 *
 * Два признака, оба уже в диапазоне [0, 1]:
 *   0 — packetSize   средний размер пакета в сессии
 *   1 — synNoReply   доля SYN-пакетов, на которые не пришло ответа
 *
 * Метки классов: +1 аномальный трафик, -1 легитимный. Именно в таком виде
 * (а не 0/1) метки нужны для hinge loss.
 *
 * ГЛАВНЫЙ УЧЕБНЫЙ СЮЖЕТ — параметр C, цена нарушения зазора. Проверено
 * симуляцией (см. _agent/calibration/sim_svm.py):
 *
 *   на чистых данных   C=0.20 → зазор 0.7228, опорных 86, точность 0.9875
 *                      C=5.00 → зазор 0.2631, опорных 19, точность 0.9875
 *   при 25% выбросов   C=1.00 → точность 0.8125  (максимум)
 *                      C=25.0 → точность 0.7625  (подгонка под выбросы)
 *
 * То есть на чистых данных ширина зазора почти не влияет на результат, а на
 * данных с выбросами у C появляется настоящий оптимум.
 *
 * ВНИМАНИЕ ПРИ ПРАВКАХ. Перечисление [SvmKernel] объявлено здесь, но
 * используется ТАКЖЕ в SvmLabMilitary.kt, SvmInteractiveMilitary.kt и
 * SvmResultMilitary.kt, причём в двух из них — в исчерпывающих `when` без
 * else. Добавление нового значения уронит сборку этих файлов. Для SVM это
 * и не нужно: линейное и RBF-ядро — канонический набор.
 *
 * Генератор — тот же LCG с константами java.util.Random, что и в питоновском
 * листинге раздела «Код»: корпус сессий побитово совпадает с уроком.
 */
class FlowSample(val x: DoubleArray, val label: Int)

enum class SvmKernel(val label: String) {
    LINEAR("Линейное"),
    RBF("RBF")
}

object SvmLab {

    const val N_FEAT = 2

    val featureNames = listOf("Средний размер пакета", "Доля SYN без ответа")

    /** (среднее размера пакета, разброс, среднее доли SYN, разброс) */
    private val LEGIT = doubleArrayOf(0.35, 0.13, 0.18, 0.11)
    private val ANOM = doubleArrayOf(0.66, 0.14, 0.62, 0.15)

    // --- значения по умолчанию, подтверждённые симуляцией ---
    const val DEFAULT_C = 1.0
    const val DEFAULT_GAMMA = 2.0
    const val DEFAULT_ITERATIONS = 3000
    const val DEFAULT_OUTLIERS = 0.10
    val DEFAULT_KERNEL = SvmKernel.LINEAR

    const val C_MIN = 0.05
    const val C_MAX = 25.0
    const val GAMMA_MIN = 0.2
    const val GAMMA_MAX = 30.0
    const val ITER_MIN = 50
    const val ITER_MAX = 6000
    const val OUTLIERS_MIN = 0.0
    const val OUTLIERS_MAX = 0.40

    const val TRAIN_SIZE = 120
    const val TEST_SIZE = 80

    // ------------------------------------------------------------------
    // Логарифмические слайдеры
    // ------------------------------------------------------------------

    /** C и gamma охватывают по два с лишним порядка — на линейном слайдере
     *  вся нижняя часть диапазона была бы недостижима пальцем. */
    fun sliderToLog(t: Float, lo: Double, hi: Double): Double {
        val a = kotlin.math.ln(lo)
        val b = kotlin.math.ln(hi)
        return exp(a + t.toDouble() * (b - a))
    }

    fun logToSlider(v: Double, lo: Double, hi: Double): Float {
        val a = kotlin.math.ln(lo)
        val b = kotlin.math.ln(hi)
        return ((kotlin.math.ln(v) - a) / (b - a)).toFloat()
    }

    // ------------------------------------------------------------------
    // Корпус сессий
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
     * [outlierRate] — доля легитимных сессий, выглядящих как аномалия.
     * Это ночной бэкап: крупные пакеты, большой объём, профиль почти как у
     * эксфильтрации. Такие точки лежат в чужом углу плоскости и тянут
     * границу на себя — на них и проверяется устойчивость модели.
     */
    fun generate(seed: Long, n: Int, outlierRate: Double): List<FlowSample> {
        val rnd = Lcg(seed)
        val out = ArrayList<FlowSample>(n)
        for (i in 0 until n) {
            val isAnom = if (rnd.nextDouble() < 0.5) 1 else -1
            val p = if (isAnom == 1) ANOM else LEGIT
            var x1 = clamp01(rnd.gauss(p[0], p[1]))
            var x2 = clamp01(rnd.gauss(p[2], p[3]))
            if (isAnom == -1 && rnd.nextDouble() < outlierRate) {
                x1 = clamp01(rnd.gauss(0.80, 0.08))
                x2 = clamp01(rnd.gauss(0.70, 0.10))
            }
            out.add(FlowSample(doubleArrayOf(x1, x2), isAnom))
        }
        return out
    }

    fun trainSet(outlierRate: Double): List<FlowSample> = generate(42L, TRAIN_SIZE, outlierRate)
    fun testSet(outlierRate: Double): List<FlowSample> = generate(777L, TEST_SIZE, outlierRate)

    // ------------------------------------------------------------------
    // Ядро
    // ------------------------------------------------------------------

    /**
     * Слагаемое +1 — дополнение постоянным признаком, дающее модели свободный
     * член. Без него разделяющая гиперплоскость обязана проходить через
     * начало координат, а оба класса лежат в положительном квадранте:
     * разделить их было бы нельзя (первая версия расчёта именно из-за этого
     * давала точность 0.45 на явно разделимых данных). Сумма двух корректных
     * ядер снова является корректным ядром, так что приём законный.
     */
    fun kernelValue(a: DoubleArray, b: DoubleArray, kernel: SvmKernel, gamma: Double): Double =
        when (kernel) {
            SvmKernel.LINEAR -> a[0] * b[0] + a[1] * b[1] + 1.0
            SvmKernel.RBF -> {
                val dx = a[0] - b[0]
                val dy = a[1] - b[1]
                exp(-gamma * (dx * dx + dy * dy)) + 1.0
            }
        }

    // ------------------------------------------------------------------
    // Обучение: Pegasos
    // ------------------------------------------------------------------

    class SvmModel(
        val alpha: DoubleArray,
        val lambda: Double,
        val iterations: Int,
        val kernel: SvmKernel,
        val gamma: Double,
        val data: List<FlowSample>
    ) {
        /** Решающая функция: расстояние до границы со знаком.
         *  НЕ вероятность — об этом отдельно сказано в уроке. */
        fun decide(x: DoubleArray): Double {
            var s = 0.0
            for (j in data.indices) {
                if (alpha[j] != 0.0) {
                    s += alpha[j] * data[j].label * kernelValue(data[j].x, x, kernel, gamma)
                }
            }
            return s / (lambda * iterations)
        }

        fun classify(x: DoubleArray): Int = if (decide(x) >= 0.0) 1 else -1

        /** Явные веса и свободный член — только для линейного ядра.
         *  Свободный член берётся из константной компоненты ядра. */
        fun linearWeights(): DoubleArray? {
            if (kernel != SvmKernel.LINEAR) return null
            var w0 = 0.0
            var w1 = 0.0
            var b = 0.0
            for (j in data.indices) {
                if (alpha[j] != 0.0) {
                    val y = data[j].label
                    w0 += alpha[j] * y * data[j].x[0]
                    w1 += alpha[j] * y * data[j].x[1]
                    b += alpha[j] * y
                }
            }
            val k = lambda * iterations
            return doubleArrayOf(w0 / k, w1 / k, b / k)
        }

        /** Ширина зазора 2/||w||. Определена только для линейного ядра. */
        fun marginWidth(): Double? {
            val w = linearWeights() ?: return null
            val nrm = sqrt(w[0] * w[0] + w[1] * w[1])
            return if (nrm > 1e-9) 2.0 / nrm else null
        }

        /**
         * Опорные векторы ПО ОПРЕДЕЛЕНИЮ мягкого зазора: наблюдения на краю
         * зазора или внутри него.
         *
         * Считать их как alpha > 0 для Pegasos НЕЛЬЗЯ: коэффициент
         * накапливается за всё обучение, и ненулевым оказывается у любой
         * точки, хоть раз нарушившей зазор. Разница ощутима — 98 против 63
         * при эталонных настройках.
         */
        fun isSupportVector(i: Int): Boolean =
            data[i].label * decide(data[i].x) <= 1.0 + 1e-9

        fun supportVectorCount(): Int {
            var c = 0
            for (i in data.indices) if (isSupportVector(i)) c++
            return c
        }
    }

    fun train(
        c: Double,
        kernel: SvmKernel,
        gamma: Double,
        iterations: Int,
        data: List<FlowSample>
    ): SvmModel {
        val n = data.size
        val lambda = 1.0 / maxOf(c * n, 1e-9)
        val alpha = DoubleArray(n)
        val rnd = Lcg(12345L)

        for (t in 1..iterations) {
            val i = minOf((rnd.nextDouble() * n).toInt(), n - 1)
            var s = 0.0
            for (j in 0 until n) {
                if (alpha[j] != 0.0) {
                    s += alpha[j] * data[j].label * kernelValue(data[j].x, data[i].x, kernel, gamma)
                }
            }
            // наблюдение нарушает зазор — увеличиваем его коэффициент
            if (data[i].label * s / (lambda * t) < 1.0) alpha[i] += 1.0
        }
        return SvmModel(alpha, lambda, iterations, kernel, gamma, data)
    }

    // ------------------------------------------------------------------
    // Оценка
    // ------------------------------------------------------------------

    fun accuracy(test: List<FlowSample>, model: SvmModel): Double {
        if (test.isEmpty()) return 0.0
        var ok = 0
        for (s in test) if (model.classify(s.x) == s.label) ok++
        return ok.toDouble() / test.size
    }

    /**
     * Карта решений для RBF-ядра: явных весов там нет, поэтому граница
     * строится перебором по сетке. Возвращается значение решающей функции,
     * чтобы можно было нарисовать и границу, и зазор.
     */
    fun decisionGrid(model: SvmModel, steps: Int = 26): Array<DoubleArray> {
        val grid = Array(steps) { DoubleArray(steps) }
        for (gx in 0 until steps) {
            val x1 = (gx + 0.5) / steps
            for (gy in 0 until steps) {
                val x2 = (gy + 0.5) / steps
                grid[gx][gy] = model.decide(doubleArrayOf(x1, x2))
            }
        }
        return grid
    }
}
