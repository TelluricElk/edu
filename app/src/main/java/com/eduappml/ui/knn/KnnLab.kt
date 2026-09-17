package com.eduappml.ui.knn

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Метод k ближайших соседей на задаче триажа алертов в SOC: новый алерт
 * классифицируется по k самым похожим случаям из базы разобранных.
 *
 * Три вердикта: ложное срабатывание, подозрительно, подтверждённый инцидент.
 *
 * Два признака СПЕЦИАЛЬНО оставлены в разных единицах:
 *   0 — events    число событий в алерте, 0..500
 *   1 — offHours  доля активности вне рабочего времени, 0..1
 *
 * ГЛАВНЫЙ УЧЕБНЫЙ СЮЖЕТ — масштаб признаков. Разброс числа событий в базе
 * около 128, разброс доли нерабочего времени около 0.25: отношение примерно
 * 513, а отношение вкладов в КВАДРАТ расстояния — больше 263 тысяч. Без
 * нормализации второй признак не участвует в решении вообще, и карта решений
 * распадается на вертикальные полосы. Точность при этом падает всего с 0.86
 * до 0.80 — то есть ошибка выглядит совершенно безобидно.
 *
 * ВНИМАНИЕ ПРИ ПРАВКАХ. Перечисления [KnnMetric] и [KnnWeighting] объявлены
 * здесь, но используются ТАКЖЕ в KnnLabMilitary.kt и KnnInteractiveMilitary.kt.
 * В `KnnLabMilitary.distance` стоит исчерпывающий `when` по [KnnMetric] — при
 * добавлении нового значения его нужно дополнять там же, иначе сборка встанет.
 * Значение CHEBYSHEV было добавлено вместе с этой темой, и соответствующая
 * ветка в KnnLabMilitary.kt дописана.
 *
 * Генератор — тот же LCG с константами java.util.Random, что и в питоновском
 * листинге раздела «Код»: база алертов побитово совпадает с уроком.
 */
class AlertCase(val x: DoubleArray, val label: Int)

enum class KnnMetric(val label: String) {
    EUCLIDEAN("Евклидово"),
    MANHATTAN("Манхэттенское"),
    CHEBYSHEV("Чебышёва")
}

enum class KnnWeighting(val label: String) {
    UNIFORM("Равное"),
    DISTANCE("По расстоянию")
}

object KnnLab {

    const val N_FEAT = 2
    const val CLASS_COUNT = 3

    const val EVENTS_MAX = 500.0

    val classNames = listOf("Ложное срабатывание", "Подозрительно", "Подтверждённый инцидент")
    val classShort = listOf("ложное", "подозр.", "инцидент")

    val featureNames = listOf("Число событий", "Доля нерабочего времени")

    // --- значения по умолчанию, подтверждённые симуляцией ---
    const val DEFAULT_K = 7
    const val DEFAULT_NOISE = 0.0
    const val DEFAULT_BASE_SIZE = 240
    const val DEFAULT_NORMALIZE = true
    val DEFAULT_METRIC = KnnMetric.EUCLIDEAN
    val DEFAULT_WEIGHTING = KnnWeighting.UNIFORM

    const val K_MIN = 1
    const val K_MAX = 41
    const val NOISE_MIN = 0.0
    const val NOISE_MAX = 0.30
    const val BASE_MIN = 10
    const val BASE_MAX = 400

    const val TEST_SIZE = 150

    /** (среднее событий, разброс, среднее доли нерабочего времени, разброс) */
    private val CLASSES = arrayOf(
        doubleArrayOf(70.0, 45.0, 0.22, 0.16),
        doubleArrayOf(210.0, 70.0, 0.50, 0.18),
        doubleArrayOf(330.0, 80.0, 0.74, 0.16)
    )

    // ------------------------------------------------------------------
    // База алертов
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

    private fun clamp(v: Double, lo: Double, hi: Double): Double =
        if (v < lo) lo else if (v > hi) hi else v

    /**
     * [noise] — доля алертов с испорченным вердиктом: аналитик ошибся при
     * разборе. В реальной базе SOC такие записи есть всегда, и именно из-за
     * них выбор k перестаёт быть безразличным.
     */
    fun generate(seed: Long, n: Int, noise: Double): List<AlertCase> {
        val rnd = Lcg(seed)
        val out = ArrayList<AlertCase>(n)
        for (i in 0 until n) {
            var c = (rnd.nextDouble() * 3).toInt()
            if (c > 2) c = 2
            val p = CLASSES[c]
            val events = clamp(rnd.gauss(p[0], p[1]), 0.0, EVENTS_MAX)
            val offHours = clamp(rnd.gauss(p[2], p[3]), 0.0, 1.0)
            var label = c
            if (rnd.nextDouble() < noise) {
                label = (rnd.nextDouble() * 3).toInt()
                if (label > 2) label = 2
            }
            out.add(AlertCase(doubleArrayOf(events, offHours), label))
        }
        return out
    }

    fun baseSet(size: Int, noise: Double): List<AlertCase> = generate(42L, size, noise)
    fun testSet(): List<AlertCase> = generate(777L, TEST_SIZE, 0.0)

    // ------------------------------------------------------------------
    // Масштабы признаков
    // ------------------------------------------------------------------

    /**
     * Масштабы для нормализации — считаются ТОЛЬКО по базе, без учёта
     * контрольной выборки: масштабы являются частью модели, и подсматривать
     * в проверочные данные нельзя (это была бы утечка).
     *
     * При выключенной нормализации оба масштаба равны единице, и расстояние
     * определяется почти исключительно числом событий.
     */
    fun scales(base: List<AlertCase>, normalize: Boolean): DoubleArray {
        if (!normalize || base.isEmpty()) return doubleArrayOf(1.0, 1.0)
        val out = DoubleArray(N_FEAT)
        for (j in 0 until N_FEAT) {
            var m = 0.0
            for (c in base) m += c.x[j]
            m /= base.size
            var v = 0.0
            for (c in base) v += (c.x[j] - m) * (c.x[j] - m)
            out[j] = max(sqrt(v / base.size), 1e-9)
        }
        return out
    }

    // ------------------------------------------------------------------
    // Расстояние и классификация
    // ------------------------------------------------------------------

    fun distance(a: DoubleArray, b: DoubleArray, sc: DoubleArray, metric: KnnMetric): Double {
        val d0 = (a[0] - b[0]) / sc[0]
        val d1 = (a[1] - b[1]) / sc[1]
        return when (metric) {
            KnnMetric.EUCLIDEAN -> sqrt(d0 * d0 + d1 * d1)
            KnnMetric.MANHATTAN -> abs(d0) + abs(d1)
            KnnMetric.CHEBYSHEV -> max(abs(d0), abs(d1))
        }
    }

    class Neighbor(val case: AlertCase, val distance: Double)

    fun neighbors(
        x: DoubleArray,
        base: List<AlertCase>,
        k: Int,
        metric: KnnMetric,
        sc: DoubleArray
    ): List<Neighbor> {
        if (base.isEmpty()) return emptyList()
        return base
            .map { Neighbor(it, distance(x, it.x, sc, metric)) }
            .sortedBy { it.distance }
            .take(k.coerceIn(1, base.size))
    }

    fun classify(
        x: DoubleArray,
        base: List<AlertCase>,
        k: Int,
        metric: KnnMetric,
        weighting: KnnWeighting,
        sc: DoubleArray
    ): Int {
        val votes = DoubleArray(CLASS_COUNT)
        for (nb in neighbors(x, base, k, metric, sc)) {
            // взвешивание по расстоянию усиливает голос ближнего соседа —
            // включая ближнего соседа с ОШИБОЧНЫМ вердиктом
            votes[nb.case.label] += when (weighting) {
                KnnWeighting.UNIFORM -> 1.0
                KnnWeighting.DISTANCE -> 1.0 / (nb.distance + 1e-6)
            }
        }
        var best = 0
        for (c in 1 until CLASS_COUNT) if (votes[c] > votes[best]) best = c
        return best
    }

    /** Голоса по классам — нужны, чтобы показать разбор конкретного алерта. */
    fun votes(
        x: DoubleArray,
        base: List<AlertCase>,
        k: Int,
        metric: KnnMetric,
        weighting: KnnWeighting,
        sc: DoubleArray
    ): DoubleArray {
        val votes = DoubleArray(CLASS_COUNT)
        for (nb in neighbors(x, base, k, metric, sc)) {
            votes[nb.case.label] += when (weighting) {
                KnnWeighting.UNIFORM -> 1.0
                KnnWeighting.DISTANCE -> 1.0 / (nb.distance + 1e-6)
            }
        }
        return votes
    }

    // ------------------------------------------------------------------
    // Оценка
    // ------------------------------------------------------------------

    fun accuracy(
        test: List<AlertCase>,
        base: List<AlertCase>,
        k: Int,
        metric: KnnMetric,
        weighting: KnnWeighting,
        sc: DoubleArray
    ): Double {
        if (test.isEmpty()) return 0.0
        var ok = 0
        for (c in test) {
            if (classify(c.x, base, k, metric, weighting, sc) == c.label) ok++
        }
        return ok.toDouble() / test.size
    }

    /** Матрица ошибок 3x3: строка — истинный класс, столбец — предсказанный. */
    fun confusion(
        test: List<AlertCase>,
        base: List<AlertCase>,
        k: Int,
        metric: KnnMetric,
        weighting: KnnWeighting,
        sc: DoubleArray
    ): Array<IntArray> {
        val m = Array(CLASS_COUNT) { IntArray(CLASS_COUNT) }
        for (c in test) {
            val p = classify(c.x, base, k, metric, weighting, sc)
            m[c.label][p]++
        }
        return m
    }

    /**
     * Карта решений: предсказанный класс для каждой ячейки сетки.
     * Именно она распадается на вертикальные полосы при выключенной
     * нормализации — цвет перестаёт зависеть от вертикальной координаты.
     */
    fun decisionMap(
        base: List<AlertCase>,
        k: Int,
        metric: KnnMetric,
        weighting: KnnWeighting,
        sc: DoubleArray,
        steps: Int = 26
    ): Array<IntArray> {
        val map = Array(steps) { IntArray(steps) }
        for (gx in 0 until steps) {
            val events = (gx + 0.5) / steps * EVENTS_MAX
            for (gy in 0 until steps) {
                val offHours = (gy + 0.5) / steps
                map[gx][gy] = classify(doubleArrayOf(events, offHours), base, k, metric, weighting, sc)
            }
        }
        return map
    }

    /** Во сколько раз разброс первого признака больше разброса второго —
     *  число, которое стоит за всем сюжетом про масштаб. */
    fun scaleRatio(base: List<AlertCase>): Double {
        val sc = scales(base, true)
        return sc[0] / sc[1]
    }
}
