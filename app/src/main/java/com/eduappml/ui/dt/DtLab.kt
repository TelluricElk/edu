package com.eduappml.ui.dt

import kotlin.math.ln

/**
 * Дерево решений на задаче восстановления плейбука эскалации инцидентов.
 *
 * Два признака:
 *   0 — failedLogins  число неудачных входов перед событием, 0..50
 *   1 — privLevel     уровень привилегий учётной записи, 0..1
 *
 * ГЛАВНЫЙ УЧЕБНЫЙ СЮЖЕТ — переобучение по глубине. Проверено симуляцией
 * (см. _agent/calibration/sim_dt.py), при 15% шума разметки:
 *
 *   глубина  обучающая  контрольная
 *      1      0.7850     0.7143
 *      4      0.9150     0.8500   <- максимум контрольной
 *      8      0.9500     0.7429
 *     12      0.9600     0.7286
 *
 * Обучающая точность растёт МОНОТОННО и потому бесполезна для выбора модели.
 * Контрольная имеет максимум. Обе кривые рисуются на одном графике.
 *
 * ВТОРОЙ СЮЖЕТ — интерпретируемость. На чистых данных дерево глубины 4
 * состоит всего из пяти листьев и восстанавливает исходную политику почти
 * дословно: пороги 29.51 против 30, 0.60 против 0.60, 7.89 против 8,
 * 0.87 против 0.85.
 *
 * ВНИМАНИЕ ПРИ ПРАВКАХ. Перечисление [DtCriterion] объявлено здесь, но
 * используется ТАКЖЕ в DtLabMilitary.kt и RfLabMilitary.kt, причём в
 * исчерпывающих `when`. Новых значений добавлять нельзя — уронит сборку
 * военных файлов.
 *
 * Тема `rf` РАНЬШЕ переиспользовала отсюда CreditPoint, DtNode, DtLab.trainSet
 * и DtLab.buildTree. Связь расцеплена вместе с переводом темы на ИБ: у `rf`
 * теперь собственные типы и собственная копия кода дерева. См. RfLab.kt.
 */
class IncidentCase(val x: DoubleArray, val escalate: Boolean)

enum class DtCriterion(val label: String) {
    GINI("Джини"),
    ENTROPY("Энтропия")
}

/** Узел дерева: либо вопрос (feature + threshold), либо лист (prediction). */
class DtTreeNode(
    val feature: Int = -1,
    val threshold: Double = 0.0,
    val left: DtTreeNode? = null,
    val right: DtTreeNode? = null,
    val prediction: Boolean? = null,
    val samples: Int = 0
) {
    val isLeaf: Boolean get() = prediction != null
}

object DtLab {

    const val N_FEAT = 2
    const val LOGINS_MAX = 50.0

    val featureNames = listOf("Неудачных входов", "Уровень привилегий")
    val featureShort = listOf("входов", "привилегии")

    // --- значения по умолчанию, подтверждённые симуляцией ---
    const val DEFAULT_DEPTH = 4
    const val DEFAULT_MIN_SPLIT = 4
    const val DEFAULT_NOISE = 0.15
    val DEFAULT_CRITERION = DtCriterion.GINI

    const val DEPTH_MIN = 1
    const val DEPTH_MAX = 12
    const val MIN_SPLIT_MIN = 2
    const val MIN_SPLIT_MAX = 64
    const val NOISE_MIN = 0.0
    const val NOISE_MAX = 0.35

    const val TRAIN_SIZE = 200
    const val TEST_SIZE = 140

    // ------------------------------------------------------------------
    // История инцидентов
    // ------------------------------------------------------------------

    private class Lcg(seed: Long) {
        private var s: Long = seed and 0xFFFFFFFFFFFFL
        fun nextDouble(): Double {
            s = (s * 0x5DEECE66DL + 0xBL) and 0xFFFFFFFFFFFFL
            return (s ushr 24).toDouble() / (1L shl 24).toDouble()
        }
    }

    /**
     * Настоящая политика эскалации, из которой генерируется история.
     * Модели её не показывают — она нужна, чтобы студент мог сверить
     * выведенные деревом пороги с исходными.
     */
    fun trueRule(logins: Double, priv: Double): Boolean = when {
        priv > 0.85 -> true                    // администратор домена — всегда
        priv > 0.60 && logins > 8.0 -> true    // подбор к привилегированной учётке
        logins > 30.0 -> true                  // массовый подбор по любой учётке
        else -> false
    }

    /** [noise] — доля инцидентов с неверным вердиктом: аналитик ошибся. */
    fun generate(seed: Long, n: Int, noise: Double): List<IncidentCase> {
        val rnd = Lcg(seed)
        val out = ArrayList<IncidentCase>(n)
        for (i in 0 until n) {
            val logins = rnd.nextDouble() * LOGINS_MAX
            val priv = rnd.nextDouble()
            var label = trueRule(logins, priv)
            if (rnd.nextDouble() < noise) label = !label
            out.add(IncidentCase(doubleArrayOf(logins, priv), label))
        }
        return out
    }

    fun trainSet(noise: Double): List<IncidentCase> = generate(42L, TRAIN_SIZE, noise)
    fun testSet(noise: Double): List<IncidentCase> = generate(777L, TEST_SIZE, noise)

    // ------------------------------------------------------------------
    // Построение дерева
    // ------------------------------------------------------------------

    fun impurity(data: List<IncidentCase>, criterion: DtCriterion): Double {
        if (data.isEmpty()) return 0.0
        var pos = 0
        for (c in data) if (c.escalate) pos++
        val p = pos.toDouble() / data.size
        val q = 1.0 - p
        return when (criterion) {
            DtCriterion.GINI -> 1.0 - (p * p + q * q)
            DtCriterion.ENTROPY -> {
                fun term(v: Double) = if (v <= 0.0) 0.0 else -v * (ln(v) / ln(2.0))
                term(p) + term(q)
            }
        }
    }

    private fun majority(data: List<IncidentCase>): Boolean {
        var pos = 0
        for (c in data) if (c.escalate) pos++
        return pos >= data.size - pos
    }

    /**
     * Перебор всех признаков и всех порогов. Пороги — СЕРЕДИНЫ между
     * соседними уникальными значениями: порог ровно по наблюдению зависел бы
     * от соглашения о строгости неравенства.
     *
     * Взвешивание по размеру обязательно: без него разбиение, отрезающее одно
     * наблюдение в идеально чистый лист, выглядело бы лучшим из возможных —
     * а это и есть заучивание выборки.
     */
    private fun bestSplit(data: List<IncidentCase>, criterion: DtCriterion): Pair<Int, Double>? {
        val parent = impurity(data, criterion)
        var bestGain = 0.0
        var bestFeature = -1
        var bestThreshold = 0.0
        val n = data.size.toDouble()

        for (f in 0 until N_FEAT) {
            val values = data.map { it.x[f] }.distinct().sorted()
            for (i in 0 until values.size - 1) {
                val t = (values[i] + values[i + 1]) / 2.0
                val left = data.filter { it.x[f] <= t }
                val right = data.filter { it.x[f] > t }
                if (left.isEmpty() || right.isEmpty()) continue
                val weighted = (left.size / n) * impurity(left, criterion) +
                    (right.size / n) * impurity(right, criterion)
                if (parent - weighted > bestGain) {
                    bestGain = parent - weighted
                    bestFeature = f
                    bestThreshold = t
                }
            }
        }
        return if (bestFeature >= 0) bestFeature to bestThreshold else null
    }

    fun buildTree(
        data: List<IncidentCase>,
        criterion: DtCriterion,
        maxDepth: Int,
        minSamplesSplit: Int,
        depth: Int = 0
    ): DtTreeNode {
        if (data.isEmpty()) return DtTreeNode(prediction = false, samples = 0)
        val first = data[0].escalate
        val allSame = data.all { it.escalate == first }
        if (allSame || depth >= maxDepth || data.size < minSamplesSplit) {
            return DtTreeNode(prediction = majority(data), samples = data.size)
        }
        val split = bestSplit(data, criterion)
            ?: return DtTreeNode(prediction = majority(data), samples = data.size)
        val (feature, threshold) = split
        val left = data.filter { it.x[feature] <= threshold }
        val right = data.filter { it.x[feature] > threshold }
        if (left.isEmpty() || right.isEmpty()) {
            return DtTreeNode(prediction = majority(data), samples = data.size)
        }
        return DtTreeNode(
            feature = feature,
            threshold = threshold,
            left = buildTree(left, criterion, maxDepth, minSamplesSplit, depth + 1),
            right = buildTree(right, criterion, maxDepth, minSamplesSplit, depth + 1),
            samples = data.size
        )
    }

    // ------------------------------------------------------------------
    // Применение и метрики
    // ------------------------------------------------------------------

    fun predict(root: DtTreeNode, x: DoubleArray): Boolean {
        var node = root
        while (!node.isLeaf) {
            node = if (x[node.feature] <= node.threshold) node.left!! else node.right!!
        }
        return node.prediction!!
    }

    fun accuracy(tree: DtTreeNode, data: List<IncidentCase>): Double {
        if (data.isEmpty()) return 0.0
        var ok = 0
        for (c in data) if (predict(tree, c.x) == c.escalate) ok++
        return ok.toDouble() / data.size
    }

    fun leafCount(node: DtTreeNode): Int =
        if (node.isLeaf) 1 else leafCount(node.left!!) + leafCount(node.right!!)

    fun treeDepth(node: DtTreeNode): Int =
        if (node.isLeaf) 0 else 1 + maxOf(treeDepth(node.left!!), treeDepth(node.right!!))

    /** Кривые точности по всему диапазону глубины — то, ради чего тема. */
    class DepthCurve(val depths: IntArray, val train: DoubleArray, val test: DoubleArray)

    fun depthCurve(
        train: List<IncidentCase>,
        test: List<IncidentCase>,
        criterion: DtCriterion,
        minSamplesSplit: Int
    ): DepthCurve {
        val n = DEPTH_MAX - DEPTH_MIN + 1
        val depths = IntArray(n)
        val trainAcc = DoubleArray(n)
        val testAcc = DoubleArray(n)
        for (i in 0 until n) {
            val d = DEPTH_MIN + i
            val t = buildTree(train, criterion, d, minSamplesSplit)
            depths[i] = d
            trainAcc[i] = accuracy(t, train)
            testAcc[i] = accuracy(t, test)
        }
        return DepthCurve(depths, trainAcc, testAcc)
    }

    /** Карта решений — на ней видно, что все границы параллельны осям. */
    fun decisionMap(tree: DtTreeNode, steps: Int = 28): Array<BooleanArray> {
        val map = Array(steps) { BooleanArray(steps) }
        for (gx in 0 until steps) {
            val logins = (gx + 0.5) / steps * LOGINS_MAX
            for (gy in 0 until steps) {
                val priv = (gy + 0.5) / steps
                map[gx][gy] = predict(tree, doubleArrayOf(logins, priv))
            }
        }
        return map
    }

    /** Текстовое представление правила в листе — путь от корня. */
    fun describeNode(node: DtTreeNode): String {
        if (node.isLeaf) {
            return if (node.prediction == true) "ЭСКАЛИРОВАТЬ" else "не эскалировать"
        }
        val name = featureShort[node.feature]
        return "$name ≤ ${"%.2f".format(node.threshold)}"
    }
}
