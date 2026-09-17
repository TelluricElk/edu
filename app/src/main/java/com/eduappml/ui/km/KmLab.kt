package com.eduappml.ui.km

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Метод k средних на кластеризации потока алертов SIEM.
 *
 * Два признака, намеренно в РАЗНЫХ единицах:
 *   0 — число затронутых хостов, 1..60
 *   1 — длительность серии событий, минуты, 0..1440
 *
 * Разница диапазонов в двадцать четыре раза — и это первый сюжет темы.
 *
 * В потоке четыре настоящие группы, заметно разного размера и плотности
 * (см. [GROUPS]). Меток алгоритму не дают: кластеризация тем и отличается
 * от классификации, что учиться не на чем. Настоящая принадлежность
 * хранится только для того, чтобы показать студенту, насколько разбиение
 * совпало с реальностью.
 *
 * ЧЕТЫРЕ СЮЖЕТА, все проверены симуляцией (_agent/calibration/sim_km.py).
 *
 * 1. НОРМАЛИЗАЦИЯ. При k=4 и k-means++ с сидом 7:
 *      с нормализацией   чистота 0.9294, силуэт 0.6766
 *      без нормализации  чистота 0.7941, силуэт 0.7220
 *    Обратите внимание: без нормализации результат ХУЖЕ, а силуэт ВЫШЕ.
 *    Внутренние метрики считаются в том же кривом пространстве и потому
 *    от неверного масштаба не спасают.
 *
 * 2. ИНИЦИАЛИЗАЦИЯ. Двенадцать сидов, k=4, с нормализацией:
 *      случайная  — инерция от 2.5153 до 5.8937 (разброс 134%), шесть разных
 *                   исходов, чистота от 0.771 до 0.941
 *      k-means++  — инерция от 2.5146 до 4.1946 (разброс 67%), три исхода,
 *                   чистота от 0.865 до 0.941
 *    Хорошее решение — инерция в пределах пяти процентов от наилучшей —
 *    выпадает при k-means++ в восьми сидах из двенадцати, при случайной
 *    инициализации в двух.
 *
 * 3. ЧИСЛО КЛАСТЕРОВ. С нормализацией инерция по k: 8.71, 6.68, 2.51,
 *    1.82, 1.68, 1.21, 1.12. Силуэт: 0.635, 0.550, 0.677, 0.580, 0.548,
 *    0.533, 0.524. Максимум силуэта приходится на k=4 — настоящее число
 *    групп. Без нормализации силуэт максимален при k=2, то есть указывает
 *    на неверный ответ.
 *
 * 4. СХОДИМОСТЬ. Число переназначенных алертов по шагам при k=4:
 *    127, 16, 17, 26, 11, 1, 2, 0. Ноль означает неподвижную точку —
 *    дальше ничего не изменится никогда.
 *
 * ВНИМАНИЕ ПРИ ПРАВКАХ. Военные файлы темы (KmLabMilitary и др.) ничего
 * отсюда не берут: у них собственные SupplyPoint и CentroidMilitary.
 * Этот файл можно править свободно.
 */
class SiemAlert(val hosts: Double, val minutes: Double, val group: Int)

enum class KmInit(val label: String) {
    RANDOM("случайная"),
    PLUS_PLUS("k-means++")
}

/** Результат одного прогона: всё, что рисуется и показывается числами. */
class KmState(
    /** Центры в исходных единицах — хосты и минуты, чтобы рисовать на том же поле. */
    val centroidsHosts: DoubleArray,
    val centroidsMinutes: DoubleArray,
    val assignments: IntArray,
    val sizes: IntArray,
    val inertia: Double,
    val moved: Int,
    val silhouette: Double,
    val purity: Double
)

object KmLab {

    const val HOSTS_MAX = 60.0
    const val MINUTES_MAX = 1440.0

    /** имя, хосты от, хосты до, минуты от, минуты до, сколько алертов */
    class AlertGroup(
        val name: String,
        val hostsFrom: Double, val hostsTo: Double,
        val minutesFrom: Double, val minutesTo: Double,
        val count: Int
    )

    val GROUPS = listOf(
        AlertGroup("Массовое обновление ПО", 34.0, 56.0, 10.0, 90.0, 90),
        AlertGroup("Сканирование сети", 18.0, 52.0, 520.0, 980.0, 25),
        AlertGroup("Подбор пароля", 1.0, 6.0, 300.0, 1320.0, 35),
        AlertGroup("Боковое смещение", 3.0, 13.0, 110.0, 380.0, 20)
    )

    const val TRUE_K = 4

    // --- значения по умолчанию, подтверждённые симуляцией ---
    const val DEFAULT_K = 4
    const val DEFAULT_ITERATIONS = 20
    const val DEFAULT_SEED = 7
    const val DEFAULT_NORMALIZE = true
    val DEFAULT_INIT = KmInit.PLUS_PLUS

    const val K_MIN = 2
    const val K_MAX = 8
    const val ITER_MIN = 1
    const val ITER_MAX = 20
    const val SEED_MIN = 1
    const val SEED_MAX = 12

    val DATA_SEED = 42L

    // ------------------------------------------------------------------
    // Поток алертов
    // ------------------------------------------------------------------

    private class Lcg(seed: Long) {
        private var s: Long = seed and 0xFFFFFFFFFFFFL
        fun nextDouble(): Double {
            s = (s * 0x5DEECE66DL + 0xBL) and 0xFFFFFFFFFFFFL
            return (s ushr 24).toDouble() / (1L shl 24).toDouble()
        }
        fun nextInt(bound: Int): Int {
            val v = (nextDouble() * bound).toInt()
            return if (v >= bound) bound - 1 else v
        }
    }

    val alerts: List<SiemAlert> by lazy {
        val rnd = Lcg(DATA_SEED)
        val out = ArrayList<SiemAlert>()
        for (gi in GROUPS.indices) {
            val g = GROUPS[gi]
            for (i in 0 until g.count) {
                val h = g.hostsFrom + rnd.nextDouble() * (g.hostsTo - g.hostsFrom)
                val m = g.minutesFrom + rnd.nextDouble() * (g.minutesTo - g.minutesFrom)
                out.add(SiemAlert(h, m, gi))
            }
        }
        out
    }

    /**
     * Пространство, в котором считаются расстояния. Без нормализации минуты
     * полностью подавляют хосты: их диапазон в двадцать четыре раза шире, а
     * в квадрат расстояния разница входит ещё и возведённой в квадрат.
     */
    private fun space(normalize: Boolean): Array<DoubleArray> =
        Array(alerts.size) { i ->
            val a = alerts[i]
            if (normalize) doubleArrayOf(a.hosts / HOSTS_MAX, a.minutes / MINUTES_MAX)
            else doubleArrayOf(a.hosts, a.minutes)
        }

    private fun dist2(a: DoubleArray, b: DoubleArray): Double {
        val dx = a[0] - b[0]
        val dy = a[1] - b[1]
        return dx * dx + dy * dy
    }

    // ------------------------------------------------------------------
    // Инициализация
    // ------------------------------------------------------------------

    private fun initRandom(xs: Array<DoubleArray>, k: Int, rnd: Lcg): Array<DoubleArray> {
        val pool = ArrayList<Int>(xs.size)
        for (i in xs.indices) pool.add(i)
        return Array(k) {
            val j = pool.removeAt(rnd.nextInt(pool.size))
            xs[j].copyOf()
        }
    }

    /**
     * k-means++: первый центр случайный, каждый следующий выбирается с
     * вероятностью, пропорциональной квадрату расстояния до ближайшего уже
     * выбранного. Центры получаются разнесёнными, и алгоритм гораздо реже
     * застревает в плохом локальном минимуме.
     */
    private fun initPlusPlus(xs: Array<DoubleArray>, k: Int, rnd: Lcg): Array<DoubleArray> {
        val cs = ArrayList<DoubleArray>(k)
        cs.add(xs[rnd.nextInt(xs.size)].copyOf())
        while (cs.size < k) {
            val dd = DoubleArray(xs.size)
            var total = 0.0
            for (i in xs.indices) {
                var best = Double.MAX_VALUE
                for (c in cs) {
                    val d = dist2(xs[i], c)
                    if (d < best) best = d
                }
                dd[i] = best
                total += best
            }
            if (total <= 0.0) {
                cs.add(xs[rnd.nextInt(xs.size)].copyOf())
                continue
            }
            val t = rnd.nextDouble() * total
            var acc = 0.0
            var picked = xs.size - 1
            for (i in xs.indices) {
                acc += dd[i]
                if (acc >= t) { picked = i; break }
            }
            cs.add(xs[picked].copyOf())
        }
        return cs.toTypedArray()
    }

    // ------------------------------------------------------------------
    // Алгоритм Ллойда
    // ------------------------------------------------------------------

    /**
     * Две фазы, повторяемые [iterations] раз: приписать каждый алерт
     * ближайшему центру, затем пересчитать центры как средние своих
     * кластеров. Обе фазы не увеличивают инерцию, поэтому процедура
     * сходится — но приходит она к ближайшему локальному минимуму, а не к
     * глобальному.
     */
    fun run(
        k: Int,
        iterations: Int,
        seed: Int,
        init: KmInit,
        normalize: Boolean
    ): KmState {
        val xs = space(normalize)
        val rnd = Lcg(seed.toLong())
        val cs = if (init == KmInit.PLUS_PLUS) initPlusPlus(xs, k, rnd) else initRandom(xs, k, rnd)
        val assign = IntArray(xs.size)
        var moved = 0

        for (step in 0 until iterations) {
            moved = 0
            for (i in xs.indices) {
                var b = 0
                var bd = dist2(xs[i], cs[0])
                for (j in 1 until k) {
                    val dj = dist2(xs[i], cs[j])
                    if (dj < bd) { b = j; bd = dj }
                }
                if (assign[i] != b) moved++
                assign[i] = b
            }
            for (j in 0 until k) {
                var sx = 0.0; var sy = 0.0; var c = 0
                for (i in xs.indices) {
                    if (assign[i] == j) { sx += xs[i][0]; sy += xs[i][1]; c++ }
                }
                if (c > 0) { cs[j][0] = sx / c; cs[j][1] = sy / c }
            }
        }

        var inertia = 0.0
        for (i in xs.indices) inertia += dist2(xs[i], cs[assign[i]])

        val sizes = IntArray(k)
        for (a in assign) sizes[a]++

        // центры обратно в исходные единицы — для рисования
        val ch = DoubleArray(k)
        val cm = DoubleArray(k)
        for (j in 0 until k) {
            ch[j] = if (normalize) cs[j][0] * HOSTS_MAX else cs[j][0]
            cm[j] = if (normalize) cs[j][1] * MINUTES_MAX else cs[j][1]
        }

        return KmState(
            centroidsHosts = ch,
            centroidsMinutes = cm,
            assignments = assign,
            sizes = sizes,
            inertia = inertia,
            moved = moved,
            silhouette = silhouette(xs, assign, k),
            purity = purity(assign, k)
        )
    }

    // ------------------------------------------------------------------
    // Оценки качества
    // ------------------------------------------------------------------

    /**
     * Силуэт: для каждого алерта сравнивается среднее расстояние до своих
     * с минимальным средним расстоянием до чужого кластера. Значение близко
     * к единице — кластеры разделены, близко к нулю — сливаются.
     *
     * Считается ВНУТРИ того же пространства, что и кластеризация. Поэтому
     * при выключенной нормализации он вырастает вместе с ошибкой: меряет
     * то же кривое расстояние.
     */
    fun silhouette(xs: Array<DoubleArray>, assign: IntArray, k: Int): Double {
        if (k < 2) return 0.0
        val groups = Array(k) { j -> (xs.indices).filter { assign[it] == j } }
        var total = 0.0
        var cnt = 0
        for (i in xs.indices) {
            val own = groups[assign[i]]
            if (own.size < 2) continue
            var a = 0.0
            for (j in own) if (j != i) a += sqrt(dist2(xs[i], xs[j]))
            a /= (own.size - 1)
            var b = Double.MAX_VALUE
            for (j in 0 until k) {
                if (j == assign[i] || groups[j].isEmpty()) continue
                var v = 0.0
                for (m in groups[j]) v += sqrt(dist2(xs[i], xs[m]))
                v /= groups[j].size
                if (v < b) b = v
            }
            if (b == Double.MAX_VALUE) continue
            total += (b - a) / max(a, b)
            cnt++
        }
        return if (cnt == 0) 0.0 else total / cnt
    }

    /**
     * Чистота: какая доля алертов попала в кластер, где преобладает их
     * настоящая группа. Это ОЦЕНКА ДЛЯ УЧЕБНЫХ ЦЕЛЕЙ — в настоящей задаче
     * настоящих групп нет, иначе кластеризация была бы не нужна.
     */
    fun purity(assign: IntArray, k: Int): Double {
        var ok = 0
        for (j in 0 until k) {
            val counts = IntArray(GROUPS.size)
            for (i in assign.indices) if (assign[i] == j) counts[alerts[i].group]++
            var best = 0
            for (c in counts) if (c > best) best = c
            ok += best
        }
        return ok.toDouble() / assign.size
    }

    /** Состав кластера по настоящим группам — для расшифровки под графиком. */
    fun composition(state: KmState, cluster: Int): IntArray {
        val counts = IntArray(GROUPS.size)
        for (i in state.assignments.indices) {
            if (state.assignments[i] == cluster) counts[alerts[i].group]++
        }
        return counts
    }

    // ------------------------------------------------------------------
    // Кривые по k
    // ------------------------------------------------------------------

    class KCurves(val ks: IntArray, val inertia: DoubleArray, val silhouette: DoubleArray)

    fun kCurves(iterations: Int, seed: Int, init: KmInit, normalize: Boolean): KCurves {
        val n = K_MAX - K_MIN + 1
        val ks = IntArray(n)
        val inert = DoubleArray(n)
        val sil = DoubleArray(n)
        for (i in 0 until n) {
            val k = K_MIN + i
            val st = run(k, iterations, seed, init, normalize)
            ks[i] = k
            inert[i] = st.inertia
            sil[i] = st.silhouette
        }
        return KCurves(ks, inert, sil)
    }

    /** k с наибольшим силуэтом — то, что метрика предлагает выбрать. */
    fun bestKBySilhouette(curves: KCurves): Int {
        var b = 0
        for (i in curves.silhouette.indices) if (curves.silhouette[i] > curves.silhouette[b]) b = i
        return curves.ks[b]
    }
}
