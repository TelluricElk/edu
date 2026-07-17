package com.eduappml.ui.interactive

import android.content.res.AssetManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class Iris(val features: FloatArray, val label: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Iris
        return features.contentEquals(other.features) && label == other.label
    }

    override fun hashCode(): Int {
        var result = features.contentHashCode()
        result = 31 * result + label.hashCode()
        return result
    }
}

class KNNClassifier(private val k: Int = 3, private val metric: DistanceMetric = DistanceMetric.EUCLIDEAN) {
    private var trainData: List<Iris> = emptyList()

    fun fit(data: List<Iris>) {
        trainData = data
    }

    fun predict(features: FloatArray): String {
        if (trainData.isEmpty()) return ""

        val distances = trainData.map { iris ->
            val dist = when (metric) {
                DistanceMetric.EUCLIDEAN -> euclidean(features, iris.features)
                DistanceMetric.MANHATTAN -> manhattan(features, iris.features)
                DistanceMetric.COSINE -> cosine(features, iris.features)
            }
            iris to dist
        }.sortedBy { it.second }

        val neighbors = distances.take(k).map { it.first.label }
        val mostCommon = neighbors.groupingBy { it }.eachCount().maxBy { it.value }.key
        return mostCommon
    }

    fun evaluate(testData: List<Iris>): Double {
        if (trainData.isEmpty() || testData.isEmpty()) return 0.0
        var correct = 0
        testData.forEach { iris ->
            val predicted = predict(iris.features)
            if (predicted == iris.label) correct++
        }
        return correct.toDouble() / testData.size
    }

    private fun euclidean(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    private fun manhattan(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            sum += kotlin.math.abs(a[i] - b[i])
        }
        return sum
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return 1f - (dot / (sqrt(normA) * sqrt(normB) + 1e-10f))
    }
}

enum class DistanceMetric {
    EUCLIDEAN, MANHATTAN, COSINE
}

@Composable
fun InteractiveScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    id: String,
    screenType: String,
    title: String? = null
) {
    val context = LocalContext.current

    var data by remember { mutableStateOf<List<Iris>>(emptyList()) }
    var trainData by remember { mutableStateOf<List<Iris>>(emptyList()) }
    var testData by remember { mutableStateOf<List<Iris>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var k by remember { mutableStateOf(3) }
    var metric by remember { mutableStateOf(DistanceMetric.EUCLIDEAN) }
    var normalize by remember { mutableStateOf(true) }

    var accuracy by remember { mutableStateOf(0.0) }
    var kAccuracyMap by remember { mutableStateOf<Map<Int, Double>>(emptyMap()) }
    var confusionMatrix by remember { mutableStateOf<Map<String, Map<String, Int>>>(emptyMap()) }
    var labels by remember { mutableStateOf<List<String>>(emptyList()) }

    fun computeKAccuracy() {
        val kRange = 1..20
        val results = mutableMapOf<Int, Double>()
        for (kVal in kRange) {
            val classifier = KNNClassifier(kVal, metric)
            val train = if (normalize) normalizeData(trainData) else trainData
            val test = if (normalize) normalizeData(testData) else testData
            classifier.fit(train)
            val acc = classifier.evaluate(test)
            results[kVal] = acc
        }
        kAccuracyMap = results
    }

    fun updateCurrentAccuracy() {
        val classifier = KNNClassifier(k, metric)
        val train = if (normalize) normalizeData(trainData) else trainData
        val test = if (normalize) normalizeData(testData) else testData
        classifier.fit(train)
        accuracy = classifier.evaluate(test)
        val cm = mutableMapOf<String, MutableMap<String, Int>>()
        labels.forEach { trueLabel ->
            cm[trueLabel] = labels.associateWith { 0 }.toMutableMap()
        }
        test.forEach { iris ->
            val predicted = classifier.predict(iris.features)
            cm[iris.label]?.merge(predicted, 1, Int::plus)
        }
        confusionMatrix = cm
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val loaded = loadIrisData(context.assets)
                data = loaded
                val shuffled = loaded.shuffled()
                val splitIndex = (shuffled.size * 0.8).toInt()
                trainData = shuffled.take(splitIndex)
                testData = shuffled.drop(splitIndex)
                labels = loaded.map { it.label }.distinct().sorted()
                computeKAccuracy()
                updateCurrentAccuracy()
                isLoading = false
            } catch (e: Exception) {
                errorMessage = e.message ?: "Ошибка загрузки данных"
                isLoading = false
            }
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (errorMessage != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Ошибка: $errorMessage", color = Color.Red)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Интерактив: ${title ?: id}",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Параметры
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Параметры", color = Color.White, fontWeight = FontWeight.SemiBold)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("K = $k", color = Color.White, modifier = Modifier.width(60.dp))
                    Slider(
                        value = k.toFloat(),
                        onValueChange = { newK ->
                            k = newK.roundToInt().coerceIn(1, 20)
                        },
                        valueRange = 1f..20f,
                        steps = 18,
                        colors = SliderDefaults.colors(thumbColor = Color.White)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Метрика", color = Color.White, modifier = Modifier.width(80.dp))
                    var expanded by remember { mutableStateOf(false) }
                    Button(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth(0.6f)
                    ) {
                        Text(metric.name, color = Color.White)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.6f)
                    ) {
                        DistanceMetric.values().forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.name, color = Color.White) },
                                onClick = {
                                    metric = m
                                    expanded = false
                                    computeKAccuracy()
                                    updateCurrentAccuracy()
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Нормализация", color = Color.White, modifier = Modifier.width(120.dp))
                    Switch(
                        checked = normalize,
                        onCheckedChange = { normalize = it
                            computeKAccuracy()
                            updateCurrentAccuracy()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White)
                    )
                }

                Button(
                    onClick = {
                        computeKAccuracy()
                        updateCurrentAccuracy()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Обучить заново")
                }
            }
        }

        // График точности от K (упрощённая гистограмма)
        if (kAccuracyMap.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Точность в зависимости от K", color = Color.White, fontWeight = FontWeight.SemiBold)
                    val sorted = kAccuracyMap.toSortedMap()
                    val maxAcc = sorted.values.maxOrNull() ?: 1.0
                    Column {
                        sorted.forEach { (kVal, acc) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "$kVal",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.width(24.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(16.dp)
                                        .background(Color(0xFF4CAF50))
                                        .fillMaxWidth((acc / maxAcc).toFloat())
                                )
                                Text(
                                    text = String.format("%.2f", acc * 100) + "%",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.width(48.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Результаты
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Результаты", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Точность: ${String.format("%.2f", accuracy * 100)}%", color = Color.White)
                Text("Метрика: ${metric.name}", color = Color.White)
                Text("K = $k", color = Color.White)

                if (confusionMatrix.isNotEmpty()) {
                    Text("Матрица ошибок:", color = Color.White, fontWeight = FontWeight.Medium)
                    val labelsSorted = labels
                    labelsSorted.forEach { trueLabel ->
                        val row = confusionMatrix[trueLabel] ?: return@forEach
                        val rowStr = labelsSorted.joinToString(" ") { label ->
                            row[label]?.toString() ?: "0"
                        }
                        Text("$trueLabel: $rowStr", color = Color.White, fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = { updateCurrentAccuracy() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Протестировать на отложенной выборке")
                }
            }
        }

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Text("Назад")
        }
    }
}

private fun loadIrisData(assets: AssetManager): List<Iris> {
    val result = mutableListOf<Iris>()
    val inputStream = assets.open("datasets/iris.csv")
    BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
        reader.readLine()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val parts = line!!.split(",")
            if (parts.size == 5) {
                val features = floatArrayOf(
                    parts[0].toFloat(),
                    parts[1].toFloat(),
                    parts[2].toFloat(),
                    parts[3].toFloat()
                )
                val label = parts[4].trim()
                result.add(Iris(features, label))
            }
        }
    }
    return result
}

private fun normalizeData(data: List<Iris>): List<Iris> {
    if (data.isEmpty()) return data
    val numFeatures = data[0].features.size
    val mins = FloatArray(numFeatures) { Float.MAX_VALUE }
    val maxs = FloatArray(numFeatures) { Float.MIN_VALUE }
    data.forEach { iris ->
        iris.features.forEachIndexed { idx, value ->
            if (value < mins[idx]) mins[idx] = value
            if (value > maxs[idx]) maxs[idx] = value
        }
    }
    return data.map { iris ->
        val normFeatures = FloatArray(numFeatures) { idx ->
            val range = maxs[idx] - mins[idx]
            if (range == 0f) 0f else (iris.features[idx] - mins[idx]) / range
        }
        Iris(normFeatures, iris.label)
    }
}