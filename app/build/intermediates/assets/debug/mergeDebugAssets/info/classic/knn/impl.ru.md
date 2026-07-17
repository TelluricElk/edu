# k-NN — Программная реализация

```kotlin
data class LabeledPoint(val x: FloatArray, val y: Int)

fun knnPredict(x: FloatArray, data: List<LabeledPoint>, k: Int = 3): Int {
    val neighbors = data
        .asSequence()
        .map { it to distance(x, it.x) }
        .sortedBy { it.second }
        .take(k)
        .map { it.first.y }
        .groupingBy { it }
        .eachCount()
        .maxBy { it.value }
        .key
    return neighbors
}

private fun distance(a: FloatArray, b: FloatArray): Float =
    kotlin.math.sqrt(a.indices.sumOf { (a[it] - b[it]).toDouble().pow(2.0) }.toFloat())