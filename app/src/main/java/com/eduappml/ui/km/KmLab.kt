package com.eduappml.ui.km

import kotlin.random.Random

data class CustomerPoint(val spending: Float, val frequency: Float)
data class Centroid(val spending: Float, val frequency: Float)

object KmLab {
    const val SPENDING_MIN = 5f
    const val SPENDING_MAX = 100f
    const val FREQUENCY_MIN = 1f
    const val FREQUENCY_MAX = 20f

    /** Синтетические покупатели — намеренно с четырьмя размытыми "естественными" скоплениями. */
    val points: List<CustomerPoint> by lazy { generate(seed = 42) }

    private fun generate(seed: Int): List<CustomerPoint> {
        val rnd = Random(seed)
        val centers = listOf(
            20f to 3f,    // редкие и малые покупки
            20f to 15f,   // частые, но малые покупки
            80f to 4f,    // редкие, но крупные покупки
            80f to 16f    // частые и крупные покупки — лучшие клиенты
        )
        val points = mutableListOf<CustomerPoint>()
        centers.forEach { (cs, cf) ->
            repeat(20) {
                val s = (cs + (rnd.nextFloat() - 0.5f) * 22f).coerceIn(SPENDING_MIN, SPENDING_MAX)
                val f = (cf + (rnd.nextFloat() - 0.5f) * 5f).coerceIn(FREQUENCY_MIN, FREQUENCY_MAX)
                points.add(CustomerPoint(s, f))
            }
        }
        return points
    }

    private fun distanceSq(p: CustomerPoint, c: Centroid): Float {
        val ds = p.spending - c.spending
        val df = p.frequency - c.frequency
        return ds * ds + df * df
    }

    data class KMeansState(val centroids: List<Centroid>, val assignments: List<Int>, val inertia: Float, val iteration: Int)

    /** Один полный прогон алгоритма Ллойда на [iterations] шагов — настоящий пересчёт, не имитация. */
    fun run(k: Int, iterations: Int, seed: Int): KMeansState {
        val rnd = Random(seed)
        var centroids = points.shuffled(rnd).take(k).map { Centroid(it.spending, it.frequency) }
        var assignments = List(points.size) { 0 }

        repeat(iterations) {
            assignments = points.map { p -> centroids.indices.minByOrNull { distanceSq(p, centroids[it]) } ?: 0 }
            centroids = centroids.indices.map { j ->
                val cluster = points.filterIndexed { idx, _ -> assignments[idx] == j }
                if (cluster.isEmpty()) centroids[j]
                else Centroid(
                    cluster.map { it.spending }.average().toFloat(),
                    cluster.map { it.frequency }.average().toFloat()
                )
            }
        }

        val inertia = points.indices.sumOf { i -> distanceSq(points[i], centroids[assignments[i]]).toDouble() }.toFloat()
        return KMeansState(centroids, assignments, inertia, iterations)
    }

    /** Инерция для метода локтя — при разных k, с фиксированным числом итераций до сходимости. */
    fun elbowSeries(maxK: Int): List<Float> = (1..maxK).map { k -> run(k, iterations = 12, seed = 7).inertia }
}
