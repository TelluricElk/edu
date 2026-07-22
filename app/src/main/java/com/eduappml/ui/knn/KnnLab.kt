package com.eduappml.ui.knn

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Точка "эталонной задачи" k-NN: классификация фрукта по двум признакам —
 * сладость (0..10) и размер (0..10). Три класса: Яблоко / Апельсин / Лимон.
 *
 * Датасет полностью синтетический и генерируется на лету с фиксированным seed,
 * поэтому одинаков при каждом запуске приложения. Никакого реального обучения
 * модели на устройстве не происходит — это учебная симуляция k-NN поверх
 * заранее заданных точек.
 */
data class FruitPoint(
    val sweetness: Float,
    val size: Float,
    val label: String
)

enum class KnnMetric(val label: String) {
    EUCLIDEAN("Евклидово"),
    MANHATTAN("Манхэттенское")
}

enum class KnnWeighting(val label: String) {
    UNIFORM("Равное"),
    DISTANCE("По расстоянию")
}

object KnnLab {

    const val FEATURE_MIN = 0f
    const val FEATURE_MAX = 10f

    val classLabels = listOf("Яблоко", "Апельсин", "Лимон")

    val classColors: Map<String, Color> = mapOf(
        "Яблоко" to Color(0xFFEF5350),
        "Апельсин" to Color(0xFFFFA726),
        "Лимон" to Color(0xFFDCE775)
    )

    /** Обучающая выборка — одна и та же на каждом запуске (seed = 42). */
    val trainSet: List<FruitPoint> by lazy { generate(seed = 42, perClass = 24) }

    /** Отложенная (контрольная) выборка — для честной проверки точности. */
    val testSet: List<FruitPoint> by lazy { generate(seed = 777, perClass = 12) }

    /** Рекомендуемые "эталонные" гиперпараметры, которые показываются в блоке решения. */
    val referenceK = 5
    val referenceMetric = KnnMetric.EUCLIDEAN
    val referenceWeighting = KnnWeighting.DISTANCE

    private fun generate(seed: Int, perClass: Int): List<FruitPoint> {
        val rnd = Random(seed)
        // (сладость, размер) — центр облака точек для каждого класса
        val centers = listOf(
            "Яблоко" to (7.3f to 6.2f),
            "Апельсин" to (6.2f to 8.4f),
            "Лимон" to (2.2f to 4.0f)
        )
        val points = mutableListOf<FruitPoint>()
        centers.forEach { (label, center) ->
            repeat(perClass) {
                val sweetness = (center.first + (rnd.nextFloat() * 2.6f - 1.3f)).coerceIn(FEATURE_MIN, FEATURE_MAX)
                val size = (center.second + (rnd.nextFloat() * 2.6f - 1.3f)).coerceIn(FEATURE_MIN, FEATURE_MAX)
                points.add(FruitPoint(sweetness, size, label))
            }
        }
        return points
    }

    private fun distance(a: FruitPoint, sweetness: Float, size: Float, metric: KnnMetric): Float =
        when (metric) {
            KnnMetric.EUCLIDEAN -> sqrt((a.sweetness - sweetness).pow(2) + (a.size - size).pow(2))
            KnnMetric.MANHATTAN -> abs(a.sweetness - sweetness) + abs(a.size - size)
        }

    data class Neighbor(val point: FruitPoint, val distance: Float)

    /** Возвращает k ближайших соседей точки (sweetness, size) в наборе [data]. */
    fun nearestNeighbors(
        sweetness: Float,
        size: Float,
        k: Int,
        metric: KnnMetric,
        data: List<FruitPoint> = trainSet
    ): List<Neighbor> {
        if (data.isEmpty()) return emptyList()
        return data
            .map { Neighbor(it, distance(it, sweetness, size, metric)) }
            .sortedBy { it.distance }
            .take(k.coerceIn(1, data.size))
    }

    /** Классификация точки методом k-NN (условный расчёт "на лету", без обучения модели). */
    fun classify(
        sweetness: Float,
        size: Float,
        k: Int,
        metric: KnnMetric,
        weighting: KnnWeighting,
        data: List<FruitPoint> = trainSet
    ): String {
        val neighbors = nearestNeighbors(sweetness, size, k, metric, data)
        if (neighbors.isEmpty()) return classLabels.first()

        val votes = mutableMapOf<String, Float>()
        neighbors.forEach { n ->
            val weight = when (weighting) {
                KnnWeighting.UNIFORM -> 1f
                KnnWeighting.DISTANCE -> 1f / (n.distance + 0.05f)
            }
            votes[n.point.label] = (votes[n.point.label] ?: 0f) + weight
        }
        return votes.maxByOrNull { it.value }?.key ?: classLabels.first()
    }

    /** Точность на отложенной выборке при заданных гиперпараметрах (0f..1f). */
    fun evaluateAccuracy(
        k: Int,
        metric: KnnMetric,
        weighting: KnnWeighting
    ): Float {
        if (testSet.isEmpty()) return 0f
        val correct = testSet.count { test ->
            classify(test.sweetness, test.size, k, metric, weighting, trainSet) == test.label
        }
        return correct.toFloat() / testSet.size
    }
}
