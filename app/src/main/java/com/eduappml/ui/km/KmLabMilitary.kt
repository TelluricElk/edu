package com.eduappml.ui.km

import kotlin.random.Random

/**
 * Военный вариант "эталонной задачи" k-средних: сегментация точек
 * снабжения по объёму заявки и частоте заявок. Полный структурный аналог
 * [KmLab] (который остаётся нетронутым и больше нигде не используется) —
 * только с военным прикладным контекстом вместо примера с покупателями.
 */
data class SupplyPoint(val requestSize: Float, val requestFrequency: Float)
data class CentroidMilitary(val requestSize: Float, val requestFrequency: Float)

object KmLabMilitary {
    const val SIZE_MIN = 5f
    const val SIZE_MAX = 100f
    const val FREQUENCY_MIN = 1f
    const val FREQUENCY_MAX = 20f

    /** Синтетические точки снабжения — намеренно с четырьмя размытыми "естественными" скоплениями. */
    val points: List<SupplyPoint> by lazy { generate(seed = 42) }

    private fun generate(seed: Int): List<SupplyPoint> {
        val rnd = Random(seed)
        val centers = listOf(
            20f to 3f,    // редкие и малые заявки
            20f to 15f,   // частые, но малые заявки
            80f to 4f,    // редкие, но крупные заявки
            80f to 16f    // частые и крупные заявки — приоритетные точки
        )
        val points = mutableListOf<SupplyPoint>()
        centers.forEach { (cs, cf) ->
            repeat(20) {
                val s = (cs + (rnd.nextFloat() - 0.5f) * 22f).coerceIn(SIZE_MIN, SIZE_MAX)
                val f = (cf + (rnd.nextFloat() - 0.5f) * 5f).coerceIn(FREQUENCY_MIN, FREQUENCY_MAX)
                points.add(SupplyPoint(s, f))
            }
        }
        return points
    }

    private fun distanceSq(p: SupplyPoint, c: CentroidMilitary): Float {
        val ds = p.requestSize - c.requestSize
        val df = p.requestFrequency - c.requestFrequency
        return ds * ds + df * df
    }

    data class KMeansState(val centroids: List<CentroidMilitary>, val assignments: List<Int>, val inertia: Float, val iteration: Int)

    /** Один полный прогон алгоритма Ллойда на [iterations] шагов — настоящий пересчёт, не имитация. */
    fun run(k: Int, iterations: Int, seed: Int): KMeansState {
        val rnd = Random(seed)
        var centroids = points.shuffled(rnd).take(k).map { CentroidMilitary(it.requestSize, it.requestFrequency) }
        var assignments = List(points.size) { 0 }

        repeat(iterations) {
            assignments = points.map { p -> centroids.indices.minByOrNull { distanceSq(p, centroids[it]) } ?: 0 }
            centroids = centroids.indices.map { j ->
                val cluster = points.filterIndexed { idx, _ -> assignments[idx] == j }
                if (cluster.isEmpty()) centroids[j]
                else CentroidMilitary(
                    cluster.map { it.requestSize }.average().toFloat(),
                    cluster.map { it.requestFrequency }.average().toFloat()
                )
            }
        }

        val inertia = points.indices.sumOf { i -> distanceSq(points[i], centroids[assignments[i]]).toDouble() }.toFloat()
        return KMeansState(centroids, assignments, inertia, iterations)
    }

    /** Инерция для метода локтя — при разных k, с фиксированным числом итераций до сходимости. */
    fun elbowSeries(maxK: Int): List<Float> = (1..maxK).map { k -> run(k, iterations = 12, seed = 7).inertia }
}
