package com.eduappml.ui.gb

import kotlin.math.exp

/**
 * Градиентный бустинг на прогнозе ущерба от инцидента по времени до его
 * обнаружения (dwell time — метрика, которую считает любой SOC).
 *
 * Один признак: сколько дней злоумышленник оставался незамеченным, 0..120.
 * Отклик: ущерб в миллионах рублей.
 *
 * ФОРМА ИСТИННОЙ ЗАВИСИМОСТИ подобрана так, чтобы прямая её принципиально
 * не выражала (см. [trueDamage]):
 *   быстрый рост в первые недели — закрепление и кража учётных данных;
 *   пологий линейный рост — продолжающаяся эксфильтрация;
 *   РАЗРЫВ на 60 днях — сработала шифровальная нагрузка.
 *
 * ГЛАВНЫЙ УЧЕБНЫЙ СЮЖЕТ — разрыв. Проверено симуляцией (см.
 * _agent/calibration/sim_gb.py) на эталонных настройках:
 *
 *   первый же пень встаёт на пороге 58.0 дня
 *   скачок предсказания между 55 и 65 днями: 3.788 при настоящем 3.830
 *   линейная регрессия на тех же данных даёт скачок 0.923
 *
 * Алгоритму никто не говорил про 60 дней — он нашёл порог сам, по остаткам.
 *
 * ВТОРОЙ СЮЖЕТ — размен «скорость обучения против числа итераций».
 * Глубина 1, контрольная MSE:
 *
 *   скорость  минимум  на итерации  на 200 итерациях
 *     1.00     2.1980       38           2.5118
 *     0.50     1.9946       18           2.1809
 *     0.20     1.9487       35           1.9805
 *     0.10     1.9526       65           1.9687
 *     0.05     1.9528      185           1.9572
 *     0.02     2.1033      200           2.1033
 *
 * Мелкий шаг даёт лучшее решение, но требует больше итераций; слишком
 * мелкий (0.02) не успевает дойти за отпущенные 200. Произведение шага на
 * число итераций до минимума держится в районе 6–9.
 *
 * ТРЕТИЙ СЮЖЕТ — глубина. При одном признаке глубина только вредит:
 * глубина 2 со скоростью 1.0 достигает минимума на ВТОРОЙ итерации
 * (1.8919), а к двухсотой уползает до 3.0115 при обучающей 0.0531.
 *
 * ВНИМАНИЕ ПРИ ПРАВКАХ. Военные файлы темы (GbLabMilitary и др.) ничего
 * отсюда не берут: они работают через com.eduappml.ui.lr.LrLabMilitary и
 * собственный тип StumpMilitary. Этот файл можно править свободно.
 */
class DamageCase(val dwell: Double, val damage: Double)

/** Узел регрессионного дерева по одному признаку. */
class GbTreeNode(
    val threshold: Double = 0.0,
    val left: GbTreeNode? = null,
    val right: GbTreeNode? = null,
    val value: Double? = null
) {
    val isLeaf: Boolean get() = value != null
}

/** Обученная модель: начальное приближение, деревья и шаг. */
class GbModel(
    val f0: Double,
    val trees: List<GbTreeNode>,
    val learningRate: Double,
    val trainMse: DoubleArray,
    val testMse: DoubleArray
)

object GbLab {

    const val DWELL_MAX = 120.0
    const val STEP_DAY = 60.0

    // --- значения по умолчанию, подтверждённые симуляцией ---
    const val DEFAULT_ESTIMATORS = 65
    const val DEFAULT_LEARNING_RATE = 0.10
    const val DEFAULT_DEPTH = 1
    const val DEFAULT_MIN_LEAF = 3
    const val DEFAULT_NOISE = 2.2

    const val ESTIMATORS_MIN = 1
    const val ESTIMATORS_MAX = 200
    const val LR_MIN = 0.02
    const val LR_MAX = 1.0
    const val DEPTH_MIN = 1
    const val DEPTH_MAX = 3
    const val MIN_LEAF_MIN = 2
    const val MIN_LEAF_MAX = 12
    const val NOISE_MIN = 0.0
    const val NOISE_MAX = 4.0

    const val TRAIN_SIZE = 40
    const val TEST_SIZE = 40

    const val TRAIN_SEED = 42L
    const val TEST_SEED = 909L

    // ------------------------------------------------------------------
    // Данные
    // ------------------------------------------------------------------

    /**
     * Тот же LCG, что в java.util.Random, но без скремблирования сида —
     * благодаря этому листинг на Python из раздела «Реализация» выдаёт ровно
     * те числа, которые показывает экран.
     */
    private class Lcg(seed: Long) {
        private var s: Long = seed and 0xFFFFFFFFFFFFL
        fun nextDouble(): Double {
            s = (s * 0x5DEECE66DL + 0xBL) and 0xFFFFFFFFFFFFL
            return (s ushr 24).toDouble() / (1L shl 24).toDouble()
        }
    }

    /** Настоящая зависимость ущерба от времени обнаружения. Моделям не показывается. */
    fun trueDamage(dwell: Double): Double {
        var v = 0.8 + 6.5 * (1.0 - exp(-dwell / 18.0)) + 0.02 * dwell
        if (dwell > STEP_DAY) v += 3.5
        return v
    }

    fun generate(seed: Long, n: Int, noise: Double): List<DamageCase> {
        val rnd = Lcg(seed)
        val out = ArrayList<DamageCase>(n)
        for (i in 0 until n) {
            val d = rnd.nextDouble() * DWELL_MAX
            val y = trueDamage(d) + (rnd.nextDouble() * 2.0 - 1.0) * noise
            out.add(DamageCase(d, y))
        }
        return out
    }

    fun trainSet(noise: Double): List<DamageCase> = generate(TRAIN_SEED, TRAIN_SIZE, noise)
    fun testSet(noise: Double): List<DamageCase> = generate(TEST_SEED, TEST_SIZE, noise)

    // ------------------------------------------------------------------
    // Регрессионное дерево по остаткам
    // ------------------------------------------------------------------

    /**
     * Разбиение выбирается по максимуму величины
     *   sumL^2 / nL + sumR^2 / nR,
     * что эквивалентно минимуму суммы квадратов отклонений внутри половин, но
     * считается за один проход по отсортированным значениям: суммы слева
     * накапливаются, суммы справа получаются вычитанием.
     */
    private fun buildTree(
        xs: DoubleArray,
        residuals: DoubleArray,
        idx: IntArray,
        depth: Int,
        minLeaf: Int
    ): GbTreeNode {
        val n = idx.size
        if (n == 0) return GbTreeNode(value = 0.0)
        var total = 0.0
        for (i in idx) total += residuals[i]
        if (depth == 0 || n < 2 * minLeaf) return GbTreeNode(value = total / n)

        val order = idx.sortedBy { xs[it] }
        var bestGain = -1.0
        var bestThreshold = 0.0
        var bestK = -1
        var leftSum = 0.0
        for (k in 0 until n - 1) {
            leftSum += residuals[order[k]]
            if (xs[order[k]] == xs[order[k + 1]]) continue
            val nl = k + 1
            val nr = n - nl
            if (nl < minLeaf || nr < minLeaf) continue
            val rightSum = total - leftSum
            val gain = leftSum * leftSum / nl + rightSum * rightSum / nr
            if (gain > bestGain) {
                bestGain = gain
                bestK = k
                bestThreshold = (xs[order[k]] + xs[order[k + 1]]) / 2.0
            }
        }
        if (bestK < 0) return GbTreeNode(value = total / n)

        val leftList = ArrayList<Int>(bestK + 1)
        val rightList = ArrayList<Int>(n - bestK - 1)
        for (i in order) {
            if (xs[i] <= bestThreshold) leftList.add(i) else rightList.add(i)
        }
        return GbTreeNode(
            threshold = bestThreshold,
            left = buildTree(xs, residuals, leftList.toIntArray(), depth - 1, minLeaf),
            right = buildTree(xs, residuals, rightList.toIntArray(), depth - 1, minLeaf)
        )
    }

    fun treePredict(node: GbTreeNode, x: Double): Double {
        var cur = node
        while (!cur.isLeaf) {
            cur = if (x <= cur.threshold) cur.left!! else cur.right!!
        }
        return cur.value!!
    }

    // ------------------------------------------------------------------
    // Бустинг
    // ------------------------------------------------------------------

    /**
     * Градиентный бустинг для квадратичной ошибки. Для неё антиградиент
     * совпадает с обычным остатком, поэтому каждое новое дерево обучается
     * прямо на том, чего не хватает текущей сумме — никакого отдельного
     * вычисления градиента в коде нет и не должно быть.
     */
    fun train(
        train: List<DamageCase>,
        test: List<DamageCase>,
        nEstimators: Int,
        learningRate: Double,
        depth: Int,
        minLeaf: Int
    ): GbModel {
        val n = train.size
        val xs = DoubleArray(n) { train[it].dwell }
        val ys = DoubleArray(n) { train[it].damage }
        var f0 = 0.0
        for (y in ys) f0 += y
        f0 /= n

        val pred = DoubleArray(n) { f0 }
        val testPred = DoubleArray(test.size) { f0 }
        val idx = IntArray(n) { it }
        val trees = ArrayList<GbTreeNode>(nEstimators)
        val trainMse = DoubleArray(nEstimators)
        val testMse = DoubleArray(nEstimators)

        for (m in 0 until nEstimators) {
            val residuals = DoubleArray(n) { ys[it] - pred[it] }
            val tree = buildTree(xs, residuals, idx, depth, minLeaf)
            trees.add(tree)

            var trSum = 0.0
            for (i in 0 until n) {
                pred[i] += learningRate * treePredict(tree, xs[i])
                val e = ys[i] - pred[i]
                trSum += e * e
            }
            trainMse[m] = trSum / n

            var teSum = 0.0
            for (i in test.indices) {
                testPred[i] += learningRate * treePredict(tree, test[i].dwell)
                val e = test[i].damage - testPred[i]
                teSum += e * e
            }
            testMse[m] = if (test.isEmpty()) 0.0 else teSum / test.size
        }
        return GbModel(f0, trees, learningRate, trainMse, testMse)
    }

    fun predict(model: GbModel, x: Double): Double {
        var p = model.f0
        for (t in model.trees) p += model.learningRate * treePredict(t, x)
        return p
    }

    /** Предсказание суммой только первых [upTo] деревьев — для показа промежуточных шагов. */
    fun predictPrefix(model: GbModel, x: Double, upTo: Int): Double {
        var p = model.f0
        var i = 0
        while (i < upTo && i < model.trees.size) {
            p += model.learningRate * treePredict(model.trees[i], x)
            i++
        }
        return p
    }

    fun mse(model: GbModel, data: List<DamageCase>): Double {
        if (data.isEmpty()) return 0.0
        var s = 0.0
        for (c in data) {
            val e = predict(model, c.dwell) - c.damage
            s += e * e
        }
        return s / data.size
    }

    /** MSE модели-константы: с чем сравнивать всё остальное. */
    fun baselineMse(train: List<DamageCase>, data: List<DamageCase>): Double {
        if (data.isEmpty() || train.isEmpty()) return 0.0
        var m = 0.0
        for (c in train) m += c.damage
        m /= train.size
        var s = 0.0
        for (c in data) {
            val e = m - c.damage
            s += e * e
        }
        return s / data.size
    }

    /** Наименьших квадратов прямая — для честного сравнения с темой «Линейная регрессия». */
    class Line(val slope: Double, val intercept: Double, val testMse: Double)

    fun fitLine(train: List<DamageCase>, test: List<DamageCase>): Line {
        val n = train.size
        if (n < 2) return Line(0.0, 0.0, 0.0)
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        for (c in train) {
            sx += c.dwell; sy += c.damage
            sxx += c.dwell * c.dwell; sxy += c.dwell * c.damage
        }
        val denom = n * sxx - sx * sx
        val k = if (denom == 0.0) 0.0 else (n * sxy - sx * sy) / denom
        val b = (sy - k * sx) / n
        var s = 0.0
        for (c in test) {
            val e = k * c.dwell + b - c.damage
            s += e * e
        }
        return Line(k, b, if (test.isEmpty()) 0.0 else s / test.size)
    }

    /** Номер итерации с наименьшей контрольной ошибкой (с нуля). */
    fun bestIteration(model: GbModel): Int {
        if (model.testMse.isEmpty()) return 0
        var best = 0
        for (i in model.testMse.indices) if (model.testMse[i] < model.testMse[best]) best = i
        return best
    }

    /** Остатки после [upTo] деревьев — то, на чём будет учиться следующее. */
    fun residuals(model: GbModel, data: List<DamageCase>, upTo: Int): DoubleArray =
        DoubleArray(data.size) { data[it].damage - predictPrefix(model, data[it].dwell, upTo) }

    /** Кривая модели по сетке — ступенчатая ломаная для отрисовки. */
    fun curve(model: GbModel, upTo: Int, steps: Int = 240): DoubleArray =
        DoubleArray(steps) { predictPrefix(model, (it + 0.5) / steps * DWELL_MAX, upTo) }

    fun trueCurve(steps: Int = 240): DoubleArray =
        DoubleArray(steps) { trueDamage((it + 0.5) / steps * DWELL_MAX) }
}
