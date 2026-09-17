package com.eduappml.ui.rf

import kotlin.math.ln

/**
 * Случайный лес на задаче статического детекта вредоносного ПО по признакам
 * PE-файла (portable executable — формат exe/dll в Windows).
 *
 * Шесть признаков, все извлекаются из файла без запуска:
 *   0 — энтропия секции .text, 4.5..8.0 бит на байт
 *   1 — доля подозрительных импортов в таблице импорта, 0..1
 *   2 — число секций в PE-заголовке, 2..12
 *   3 — размер оверлея (данные за пределами последней секции), 0..400 КБ
 *   4 — достоверность цифровой подписи, 0..1
 *   5 — доля URL-подобных строк в файле, 0..1
 *
 * ГЛАВНЫЙ УЧЕБНЫЙ СЮЖЕТ — бэггинг лечит переобучение дерева. Проверено
 * симуляцией (см. _agent/calibration/sim_rf.py), при 10% шума разметки,
 * 25 деревьях и mtry=2:
 *
 *   глубина  одно дерево (обуч/контр)  лес (обуч/контр)
 *      2         0.8650 / 0.8300        0.7825 / 0.6975
 *      4         0.8725 / 0.8250        0.8775 / 0.8400
 *      8         0.9725 / 0.7675        0.9450 / 0.8450
 *     10         0.9825 / 0.7575        0.9750 / 0.8400
 *     14         0.9875 / 0.7400        0.9875 / 0.8325
 *
 * Одиночное дерево на глубине 14 теряет 0.0975 контрольной точности против
 * своего же максимума. Лес из тех же самых деревьев не теряет ничего: на
 * глубине 10 он даёт 0.8400 против 0.7575 у дерева — плюс 8.25 пункта,
 * причём код дерева не изменился ни на строку.
 *
 * ВТОРОЙ СЮЖЕТ — откуда берётся разнообразие деревьев. Их ровно два
 * источника, и интерактив позволяет выключить любой:
 *
 *   mtry  бутстрап вкл  бутстрап выкл
 *     1      0.8325        0.8375
 *     2      0.8400        0.8275
 *     4      0.8250        0.8125
 *     6      0.8375        0.7575
 *
 * Правый нижний угол — 0.7575 — это ровно точность ОДНОГО дерева. Без
 * бутстрапа и без подпространства признаков все 25 деревьев детерминированы
 * и потому идентичны: лес выродился в свой единственный элемент. Стоит
 * включить любой из двух механизмов — и он оживает.
 *
 * ТРЕТИЙ СЮЖЕТ — OOB. Каждое дерево не видело примерно трети обучающих
 * файлов, и на них его можно честно проверить, не заводя отдельную
 * контрольную выборку. OOB-оценка (0.8275) идёт вплотную к контрольной
 * (0.8400) и растёт монотоннее.
 *
 * ВНИМАНИЕ ПРИ ПРАВКАХ. Военные файлы RfLabMilitary/RfInteractiveMilitary/
 * RfResultMilitary НИЧЕГО отсюда не берут — они работают через
 * com.eduappml.ui.dt.DtLabMilitary. Этот файл можно править свободно.
 * Обратной зависимости тоже нет: тема `dt` на `rf` не ссылается.
 */
class PeSample(val x: DoubleArray, val malware: Boolean)

enum class RfCriterion(val label: String) {
    GINI("Джини"),
    ENTROPY("Энтропия")
}

/** Узел дерева: либо вопрос (feature + threshold), либо лист (prediction). */
class RfTreeNode(
    val feature: Int = -1,
    val threshold: Double = 0.0,
    val left: RfTreeNode? = null,
    val right: RfTreeNode? = null,
    val prediction: Boolean? = null,
    val samples: Int = 0
) {
    val isLeaf: Boolean get() = prediction != null
}

/** Обученный лес вместе с информацией, кто чего не видел. */
class RfForest(
    val trees: List<RfTreeNode>,
    /** Для каждого дерева — индексы обучающих файлов, НЕ попавших в его выборку. */
    val oob: List<IntArray>
)

object RfLab {

    const val N_FEAT = 6

    val featureNames = listOf(
        "Энтропия .text",
        "Доля подозрительных импортов",
        "Число секций",
        "Размер оверлея",
        "Достоверность подписи",
        "Доля URL-строк"
    )
    val featureShort = listOf("энтропия", "импорты", "секции", "оверлей", "подпись", "URL")
    val featureUnits = listOf("бит/байт", "", "шт", "КБ", "", "")

    private val featureMin = doubleArrayOf(4.5, 0.0, 2.0, 0.0, 0.0, 0.0)
    private val featureMax = doubleArrayOf(8.0, 1.0, 12.0, 400.0, 1.0, 1.0)

    // --- значения по умолчанию, подтверждённые симуляцией ---
    const val DEFAULT_TREES = 25
    const val DEFAULT_DEPTH = 10
    const val DEFAULT_MTRY = 2
    const val DEFAULT_MIN_SPLIT = 4
    const val DEFAULT_NOISE = 0.10
    const val DEFAULT_BOOTSTRAP = true
    val DEFAULT_CRITERION = RfCriterion.GINI

    const val TREES_MIN = 1
    const val TREES_MAX = 50
    const val DEPTH_MIN = 1
    const val DEPTH_MAX = 14
    const val MTRY_MIN = 1
    const val MTRY_MAX = N_FEAT
    const val MIN_SPLIT_MIN = 2
    const val MIN_SPLIT_MAX = 64
    const val NOISE_MIN = 0.0
    const val NOISE_MAX = 0.35

    const val TRAIN_SIZE = 400
    const val TEST_SIZE = 400

    const val TRAIN_SEED = 42L
    const val TEST_SEED = 909L

    // ------------------------------------------------------------------
    // Коллекция образцов
    // ------------------------------------------------------------------

    /**
     * Тот же LCG, что и в java.util.Random, но без скремблирования сида.
     * Благодаря этому листинг на Python из раздела «Реализация» выдаёт
     * ровно те числа, которые показывает приложение.
     */
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

    /**
     * Настоящее правило, по которому размечена коллекция. Модели его не
     * показывают; оно нужно, чтобы студент понимал, почему одно дерево
     * здесь принципиально не справляется.
     *
     * Правило нелинейно и содержит два взаимодействия признаков:
     * упакованный код вместе с подозрительными импортами усиливают друг
     * друга, а действительная подпись при чистой таблице импорта, наоборот,
     * снимает подозрения. Ни одно, ни другое не выражается разрезом по
     * одному признаку — их приходится собирать из нескольких уровней дерева.
     */
    fun trueRule(x: DoubleArray): Boolean {
        val ent = x[0]; val susp = x[1]; val sec = x[2]
        val ovl = x[3]; val sign = x[4]; val urls = x[5]
        var score = 3.4 * (ent - 6.25) / 1.75 +
            1.0 * (susp - 0.5) +
            0.55 * (urls - 0.5) +
            0.45 * ((sec - 7.0) / 5.0) +
            0.5 * (ovl / 400.0 - 0.5) -
            0.9 * (sign - 0.5)
        if (ent > 7.0 && susp > 0.6) score += 1.3
        if (sign > 0.8 && susp < 0.3) score -= 1.1
        return score > 0.30
    }

    /** [noise] — доля файлов с неверным вердиктом: аналитик ошибся. */
    fun generate(seed: Long, n: Int, noise: Double): List<PeSample> {
        val rnd = Lcg(seed)
        val out = ArrayList<PeSample>(n)
        for (i in 0 until n) {
            val x = DoubleArray(N_FEAT)
            for (f in 0 until N_FEAT) {
                x[f] = featureMin[f] + rnd.nextDouble() * (featureMax[f] - featureMin[f])
            }
            var label = trueRule(x)
            if (rnd.nextDouble() < noise) label = !label
            out.add(PeSample(x, label))
        }
        return out
    }

    fun trainSet(noise: Double): List<PeSample> = generate(TRAIN_SEED, TRAIN_SIZE, noise)
    fun testSet(noise: Double): List<PeSample> = generate(TEST_SEED, TEST_SIZE, noise)

    // ------------------------------------------------------------------
    // Дерево
    // ------------------------------------------------------------------

    private fun impurityOf(pos: Int, neg: Int, criterion: RfCriterion): Double {
        val total = pos + neg
        if (total == 0) return 0.0
        val p = pos.toDouble() / total
        val q = neg.toDouble() / total
        return when (criterion) {
            RfCriterion.GINI -> 1.0 - (p * p + q * q)
            RfCriterion.ENTROPY -> {
                var r = 0.0
                if (p > 0.0) r -= p * (ln(p) / ln(2.0))
                if (q > 0.0) r -= q * (ln(q) / ln(2.0))
                r
            }
        }
    }

    fun impurity(data: List<PeSample>, criterion: RfCriterion): Double {
        var pos = 0
        for (c in data) if (c.malware) pos++
        return impurityOf(pos, data.size - pos, criterion)
    }

    private fun majority(data: List<PeSample>): Boolean {
        var pos = 0
        for (c in data) if (c.malware) pos++
        return pos >= data.size - pos
    }

    /**
     * Перебор порогов одним проходом по отсортированным значениям с
     * накопительными счётчиками: O(n log n) на признак вместо O(n^2).
     * Наивная версия пересчитывала бы лес заметно дольше кадра, и ползунки
     * перестали бы ощущаться отзывчивыми.
     *
     * Порог — середина между соседними различными значениями: порог ровно по
     * наблюдению зависел бы от соглашения о строгости неравенства.
     */
    private fun bestSplit(
        data: List<PeSample>,
        criterion: RfCriterion,
        features: IntArray
    ): Pair<Int, Double>? {
        val n = data.size
        if (n < 2) return null
        var posAll = 0
        for (c in data) if (c.malware) posAll++
        val parent = impurityOf(posAll, n - posAll, criterion)

        var bestGain = 0.0
        var bestFeature = -1
        var bestThreshold = 0.0
        val nd = n.toDouble()

        for (f in features) {
            val order = data.sortedBy { it.x[f] }
            var lp = 0
            var lnn = 0
            for (i in 0 until n - 1) {
                if (order[i].malware) lp++ else lnn++
                val v = order[i].x[f]
                val w = order[i + 1].x[f]
                if (v == w) continue
                val left = lp + lnn
                val right = n - left
                val weighted = (left / nd) * impurityOf(lp, lnn, criterion) +
                    (right / nd) * impurityOf(posAll - lp, (n - posAll) - lnn, criterion)
                if (parent - weighted > bestGain) {
                    bestGain = parent - weighted
                    bestFeature = f
                    bestThreshold = (v + w) / 2.0
                }
            }
        }
        return if (bestFeature >= 0) bestFeature to bestThreshold else null
    }

    /**
     * Случайное подпространство признаков для одного узла. Именно «для узла»,
     * а не для дерева целиком: у каждого разбиения свой набор кандидатов.
     */
    private fun pickFeatures(mtry: Int, rnd: Lcg): IntArray {
        if (mtry >= N_FEAT) return IntArray(N_FEAT) { it }
        val pool = ArrayList<Int>(N_FEAT)
        for (i in 0 until N_FEAT) pool.add(i)
        val out = IntArray(mtry)
        for (k in 0 until mtry) {
            out[k] = pool.removeAt(rnd.nextInt(pool.size))
        }
        out.sort()
        return out
    }

    private fun buildTreeInner(
        data: List<PeSample>,
        criterion: RfCriterion,
        maxDepth: Int,
        minSamplesSplit: Int,
        mtry: Int,
        rnd: Lcg,
        depth: Int
    ): RfTreeNode {
        if (data.isEmpty()) return RfTreeNode(prediction = false, samples = 0)
        val first = data[0].malware
        var allSame = true
        for (c in data) if (c.malware != first) { allSame = false; break }
        if (allSame || depth >= maxDepth || data.size < minSamplesSplit) {
            return RfTreeNode(prediction = majority(data), samples = data.size)
        }
        val features = pickFeatures(mtry, rnd)
        val split = bestSplit(data, criterion, features)
            ?: return RfTreeNode(prediction = majority(data), samples = data.size)
        val (f, t) = split
        val left = data.filter { it.x[f] <= t }
        val right = data.filter { it.x[f] > t }
        if (left.isEmpty() || right.isEmpty()) {
            return RfTreeNode(prediction = majority(data), samples = data.size)
        }
        return RfTreeNode(
            feature = f,
            threshold = t,
            left = buildTreeInner(left, criterion, maxDepth, minSamplesSplit, mtry, rnd, depth + 1),
            right = buildTreeInner(right, criterion, maxDepth, minSamplesSplit, mtry, rnd, depth + 1),
            samples = data.size
        )
    }

    /** Одиночное дерево: все признаки в каждом узле, поведение детерминировано. */
    fun buildSingleTree(
        data: List<PeSample>,
        criterion: RfCriterion,
        maxDepth: Int,
        minSamplesSplit: Int
    ): RfTreeNode = buildTreeInner(data, criterion, maxDepth, minSamplesSplit, N_FEAT, Lcg(5L), 0)

    fun predict(root: RfTreeNode, x: DoubleArray): Boolean {
        var node = root
        while (!node.isLeaf) {
            node = if (x[node.feature] <= node.threshold) node.left!! else node.right!!
        }
        return node.prediction!!
    }

    fun accuracy(tree: RfTreeNode, data: List<PeSample>): Double {
        if (data.isEmpty()) return 0.0
        var ok = 0
        for (c in data) if (predict(tree, c.x) == c.malware) ok++
        return ok.toDouble() / data.size
    }

    fun leafCount(node: RfTreeNode): Int =
        if (node.isLeaf) 1 else leafCount(node.left!!) + leafCount(node.right!!)

    fun treeDepth(node: RfTreeNode): Int =
        if (node.isLeaf) 0 else 1 + maxOf(treeDepth(node.left!!), treeDepth(node.right!!))

    // ------------------------------------------------------------------
    // Лес
    // ------------------------------------------------------------------

    /**
     * Обучение леса. Два независимых источника разнообразия:
     *   [bootstrap] — своя выборка с возвратом для каждого дерева;
     *   [mtry] — своё случайное подпространство признаков для каждого узла.
     * Если выключить оба (bootstrap = false, mtry = N_FEAT), все деревья
     * получатся буквально одинаковыми, и лес выродится в одно дерево.
     */
    fun trainForest(
        train: List<PeSample>,
        criterion: RfCriterion,
        nTrees: Int,
        maxDepth: Int,
        minSamplesSplit: Int,
        mtry: Int,
        bootstrap: Boolean
    ): RfForest {
        val trees = ArrayList<RfTreeNode>(nTrees)
        val oob = ArrayList<IntArray>(nTrees)
        val n = train.size
        for (i in 0 until nTrees) {
            val rnd = Lcg(1000L + i * 7919L)
            if (bootstrap) {
                val used = BooleanArray(n)
                val sample = ArrayList<PeSample>(n)
                for (k in 0 until n) {
                    val j = rnd.nextInt(n)
                    used[j] = true
                    sample.add(train[j])
                }
                trees.add(buildTreeInner(sample, criterion, maxDepth, minSamplesSplit, mtry, rnd, 0))
                var cnt = 0
                for (j in 0 until n) if (!used[j]) cnt++
                val arr = IntArray(cnt)
                var w = 0
                for (j in 0 until n) if (!used[j]) { arr[w] = j; w++ }
                oob.add(arr)
            } else {
                trees.add(buildTreeInner(train, criterion, maxDepth, minSamplesSplit, mtry, rnd, 0))
                oob.add(IntArray(0))
            }
        }
        return RfForest(trees, oob)
    }

    /** Доля деревьев, проголосовавших «вредоносный». */
    fun voteShare(forest: RfForest, x: DoubleArray): Double {
        if (forest.trees.isEmpty()) return 0.0
        var pos = 0
        for (t in forest.trees) if (predict(t, x)) pos++
        return pos.toDouble() / forest.trees.size
    }

    fun predictForest(forest: RfForest, x: DoubleArray): Boolean {
        var pos = 0
        for (t in forest.trees) if (predict(t, x)) pos++
        return pos >= forest.trees.size - pos
    }

    fun forestAccuracy(forest: RfForest, data: List<PeSample>): Double {
        if (data.isEmpty() || forest.trees.isEmpty()) return 0.0
        var ok = 0
        for (c in data) if (predictForest(forest, c.x) == c.malware) ok++
        return ok.toDouble() / data.size
    }

    /**
     * Кривые «сколько деревьев — какая точность». Считаются накопительно за
     * один проход: голоса каждого нового дерева добавляются к уже
     * накопленным, поэтому стоимость линейна по числу деревьев, а не
     * квадратична.
     *
     * [testCurve] — точность на контрольной выборке;
     * [oobCurve]  — OOB-оценка, посчитанная вообще без контрольной выборки.
     */
    class ForestCurve(val testCurve: DoubleArray, val oobCurve: DoubleArray)

    fun forestCurve(
        forest: RfForest,
        train: List<PeSample>,
        test: List<PeSample>
    ): ForestCurve {
        val m = forest.trees.size
        val testCurve = DoubleArray(m)
        val oobCurve = DoubleArray(m)

        val testPos = IntArray(test.size)
        val oobPos = IntArray(train.size)
        val oobTotal = IntArray(train.size)

        for (k in 0 until m) {
            val tree = forest.trees[k]
            for (i in test.indices) {
                if (predict(tree, test[i].x)) testPos[i]++
            }
            for (j in forest.oob[k]) {
                oobTotal[j]++
                if (predict(tree, train[j].x)) oobPos[j]++
            }

            val votes = k + 1
            var ok = 0
            for (i in test.indices) {
                val yes = testPos[i] >= votes - testPos[i]
                if (yes == test[i].malware) ok++
            }
            testCurve[k] = if (test.isEmpty()) 0.0 else ok.toDouble() / test.size

            var oobOk = 0
            var oobCount = 0
            for (j in train.indices) {
                if (oobTotal[j] == 0) continue
                oobCount++
                val yes = oobPos[j] >= oobTotal[j] - oobPos[j]
                if (yes == train[j].malware) oobOk++
            }
            oobCurve[k] = if (oobCount == 0) 0.0 else oobOk.toDouble() / oobCount
        }
        return ForestCurve(testCurve, oobCurve)
    }

    /**
     * Важность признака — суммарный размер узлов, где по нему разбивали,
     * по всему лесу, нормированный на единицу. Считать по одному дереву
     * бессмысленно: там на первом же уровне побеждает один признак и
     * заслоняет остальные.
     */
    fun importance(forest: RfForest): DoubleArray {
        val imp = DoubleArray(N_FEAT)
        fun walk(node: RfTreeNode) {
            if (node.isLeaf) return
            imp[node.feature] += node.samples.toDouble()
            walk(node.left!!)
            walk(node.right!!)
        }
        for (t in forest.trees) walk(t)
        var sum = 0.0
        for (v in imp) sum += v
        if (sum > 0.0) for (i in imp.indices) imp[i] = imp[i] / sum
        return imp
    }

    /** Гистограмма долей голосов по контрольной выборке — 10 корзин. */
    fun voteHistogram(forest: RfForest, data: List<PeSample>): Array<IntArray> {
        val bins = Array(2) { IntArray(10) }
        for (c in data) {
            val share = voteShare(forest, c.x)
            var b = (share * 10).toInt()
            if (b > 9) b = 9
            bins[if (c.malware) 1 else 0][b]++
        }
        return bins
    }
}
