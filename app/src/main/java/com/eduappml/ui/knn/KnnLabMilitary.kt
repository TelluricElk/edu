package com.eduappml.ui.knn

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Военный вариант "эталонной задачи" k-NN: классификация воздушной цели
 * по двум признакам — скорость (0..10) и высота полёта (0..10).
 * Три класса: Свой / Гражданский / Противник.
 *
 * Это полный аналог [KnnLab] (который остаётся нетронутым и больше нигде
 * не используется) — только с военным прикладным контекстом вместо примера
 * с фруктами. Использует общие enum'ы [KnnMetric] и [KnnWeighting] из
 * KnnLab.kt, так как они не привязаны к предметной области.
 *
 * Датасет полностью синтетический и генерируется на лету с фиксированным seed,
 * поэтому одинаков при каждом запуске приложения. Никакого реального обучения
 * модели на устройстве не происходит — это учебная симуляция k-NN поверх
 * заранее заданных точек.
 */
data class AircraftPoint(
    val speed: Float,
    val altitude: Float,
    val label: String
)

object KnnLabMilitary {

    const val FEATURE_MIN = 0f
    const val FEATURE_MAX = 10f

    val classLabels = listOf("Свой", "Гражданский", "Противник")

    val classColors: Map<String, Color> = mapOf(
        "Свой" to Color(0xFF6BCB77),
        "Гражданский" to Color(0xFF4D96FF),
        "Противник" to Color(0xFFEF5350)
    )

    /** Обучающая выборка — одна и та же на каждом запуске (seed = 42). */
    val trainSet: List<AircraftPoint> by lazy { generate(seed = 42, perClass = 24) }

    /** Отложенная (контрольная) выборка — для честной проверки точности. */
    val testSet: List<AircraftPoint> by lazy { generate(seed = 777, perClass = 12) }

    /** Рекомендуемые "эталонные" гиперпараметры, которые показываются в блоке решения. */
    val referenceK = 5
    val referenceMetric = KnnMetric.EUCLIDEAN
    val referenceWeighting = KnnWeighting.DISTANCE

    private fun generate(seed: Int, perClass: Int): List<AircraftPoint> {
        val rnd = Random(seed)
        // (скорость, высота) — центр облака точек для каждого класса
        val centers = listOf(
            "Свой" to (7.3f to 6.2f),
            "Гражданский" to (6.2f to 8.4f),
            "Противник" to (2.2f to 4.0f)
        )
        val points = mutableListOf<AircraftPoint>()
        centers.forEach { (label, center) ->
            repeat(perClass) {
                val speed = (center.first + (rnd.nextFloat() * 2.6f - 1.3f)).coerceIn(FEATURE_MIN, FEATURE_MAX)
                val altitude = (center.second + (rnd.nextFloat() * 2.6f - 1.3f)).coerceIn(FEATURE_MIN, FEATURE_MAX)
                points.add(AircraftPoint(speed, altitude, label))
            }
        }
        return points
    }

    private fun distance(a: AircraftPoint, speed: Float, altitude: Float, metric: KnnMetric): Float =
        when (metric) {
            KnnMetric.EUCLIDEAN -> sqrt((a.speed - speed).pow(2) + (a.altitude - altitude).pow(2))
            KnnMetric.MANHATTAN -> abs(a.speed - speed) + abs(a.altitude - altitude)
        }

    data class Neighbor(val point: AircraftPoint, val distance: Float)

    /** Возвращает k ближайших соседей точки (speed, altitude) в наборе [data]. */
    fun nearestNeighbors(
        speed: Float,
        altitude: Float,
        k: Int,
        metric: KnnMetric,
        data: List<AircraftPoint> = trainSet
    ): List<Neighbor> {
        if (data.isEmpty()) return emptyList()
        return data
            .map { Neighbor(it, distance(it, speed, altitude, metric)) }
            .sortedBy { it.distance }
            .take(k.coerceIn(1, data.size))
    }

    /** Классификация точки методом k-NN (условный расчёт "на лету", без обучения модели). */
    fun classify(
        speed: Float,
        altitude: Float,
        k: Int,
        metric: KnnMetric,
        weighting: KnnWeighting,
        data: List<AircraftPoint> = trainSet
    ): String {
        val neighbors = nearestNeighbors(speed, altitude, k, metric, data)
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

    /** Точность классификации на контрольной выборке при заданных гиперпараметрах. */
    fun evaluateAccuracy(k: Int, metric: KnnMetric, weighting: KnnWeighting): Float {
        if (testSet.isEmpty()) return 0f
        val correct = testSet.count { point ->
            classify(point.speed, point.altitude, k, metric, weighting) == point.label
        }
        return correct.toFloat() / testSet.size
    }
}
