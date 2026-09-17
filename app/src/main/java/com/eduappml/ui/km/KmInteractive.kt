package com.eduappml.ui.km

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.common.AskChatButton
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.buildInteractiveChatPrompt
import com.eduappml.ui.common.designPx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val ClusterColors = listOf(
    Color(0xFFFFD93D),
    Color(0xFF6BCB77),
    Color(0xFF9AA7FF),
    Color(0xFFFF6B6B),
    Color(0xFF4ECDC4),
    Color(0xFFC77DFF),
    Color(0xFFFFA45B),
    Color(0xFF8FD694)
)
private val ColorAccent = Color(0xFFFFD93D)
private val ColorInertia = Color(0xFF9AA7FF)
private val ColorSil = Color(0xFF6BCB77)
private val ColorWarn = Color(0xFFFF6B6B)

/** Всё, что считается в фоне за один заход. */
private class KmComputed(
    val state: KmState,
    val curves: KmLab.KCurves,
    val bestK: Int,
    val compositions: List<IntArray>
)

/**
 * Интерактив темы «Метод k средних»: кластеризация потока алертов SIEM.
 *
 * Первый холст — само поле алертов с раскраской по кластерам и центрами.
 * Ползунок числа итераций позволяет пройти алгоритм по шагам и увидеть,
 * как центры съезжаются, а счётчик переназначенных алертов падает до нуля.
 *
 * Второй холст — кривые инерции и силуэта по числу кластеров. Инерция
 * падает всегда, поэтому по ней выбирают на глаз, ища излом; силуэт имеет
 * максимум и потому честнее.
 *
 * Третий блок — расшифровка кластеров по настоящим группам алертов.
 * Настоящих групп алгоритм не видел: они показаны только студенту.
 */
@Composable
fun KmInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = ColorAccent
    val topicTitle = title ?: "Метод k средних"

    var k by remember { mutableIntStateOf(KmLab.DEFAULT_K) }
    var iterations by remember { mutableIntStateOf(KmLab.DEFAULT_ITERATIONS) }
    var seed by remember { mutableIntStateOf(KmLab.DEFAULT_SEED) }
    var initMethod by remember { mutableStateOf(KmLab.DEFAULT_INIT) }
    var normalize by remember { mutableStateOf(KmLab.DEFAULT_NORMALIZE) }

    var computed by remember { mutableStateOf<KmComputed?>(null) }

    LaunchedEffect(k, iterations, seed, initMethod, normalize) {
        delay(150)
        computed = withContext(Dispatchers.Default) {
            val st = KmLab.run(k, iterations, seed, initMethod, normalize)
            val cv = KmLab.kCurves(KmLab.ITER_MAX, seed, initMethod, normalize)
            KmComputed(
                state = st,
                curves = cv,
                bestK = KmLab.bestKBySilhouette(cv),
                compositions = (0 until k).map { KmLab.composition(st, it) }
            )
        }
    }

    val r = computed

    LessonScaffold(
        eyebrow = "Интерактив",
        title = topicTitle,
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Text(
            "${KmLab.alerts.size} алертов за смену. По горизонтали — сколько хостов затронуто " +
                "(до ${KmLab.HOSTS_MAX.toInt()}), по вертикали — сколько минут длилась серия " +
                "событий (до ${KmLab.MINUTES_MAX.toInt()}, то есть сутки). Меток нет: аналитик " +
                "не размечал этот поток и размечать не будет — его в смену приходит несколько " +
                "тысяч. Задача — самим найти в нём группы.",
            fontSize = 14.sp, color = textColor.copy(alpha = 0.75f), lineHeight = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ---------------- Поле алертов ----------------
        Text(
            "Поток алертов и найденные центры",
            color = textColor.copy(alpha = 0.9f), fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)
        )
        ChartBox(height = 260.dp) {
            if (r != null) ScatterCanvas(r.state, k)
        }
        Text(
            "Крестами отмечены центры кластеров. Ползунок «шагов алгоритма» ниже " +
                "проигрывает процедуру по одной итерации: видно, как центры съезжаются " +
                "с начальных позиций на свои места.",
            fontSize = 12.sp, color = textColor.copy(alpha = 0.6f), lineHeight = 17.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )

        // ---------------- Выбор k ----------------
        Text(
            "Сколько кластеров брать",
            color = textColor.copy(alpha = 0.9f), fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)
        )
        ChartBox(height = 200.dp) {
            if (r != null) CurvesCanvas(r.curves, k)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendDot(ColorInertia, "инерция", textColor)
            LegendDot(ColorSil, "силуэт", textColor)
        }
        if (r != null) {
            Text(
                "Инерция падает при любом росте k — при k, равном числу алертов, она " +
                    "обратится в ноль. Поэтому по ней ищут излом на глаз, и это ненадёжно. " +
                    "Силуэт имеет максимум: сейчас он приходится на k = ${r.bestK}" +
                    (if (r.bestK == KmLab.TRUE_K) ", и это настоящее число групп в потоке."
                     else ", а настоящих групп в потоке ${KmLab.TRUE_K}."),
                fontSize = 12.sp, color = textColor.copy(alpha = 0.6f), lineHeight = 17.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
            )
        }

        // ---------------- Настройки ----------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Настройки", color = textColor,
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                SliderRow(
                    label = "Кластеров",
                    value = "$k",
                    hint = when {
                        k < KmLab.TRUE_K -> "меньше, чем настоящих групп: часть из них слипнется"
                        k > KmLab.TRUE_K -> "больше, чем настоящих групп: крупные будут разрезаны"
                        else -> "столько же, сколько настоящих групп в потоке"
                    },
                    textColor = textColor
                ) {
                    Slider(
                        value = k.toFloat(), onValueChange = { k = it.roundToInt() },
                        valueRange = KmLab.K_MIN.toFloat()..KmLab.K_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )
                }

                SliderRow(
                    label = "Шагов алгоритма",
                    value = "$iterations",
                    hint = if (r != null && r.state.moved == 0)
                        "неподвижная точка: на последнем шаге не переназначено ни одного алерта"
                    else if (r != null)
                        "на последнем шаге сменили кластер ${r.state.moved} алертов"
                    else "считаем…",
                    textColor = textColor
                ) {
                    Slider(
                        value = iterations.toFloat(), onValueChange = { iterations = it.roundToInt() },
                        valueRange = KmLab.ITER_MIN.toFloat()..KmLab.ITER_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = ColorSil, activeTrackColor = ColorSil)
                    )
                }

                SliderRow(
                    label = "Случайный сид",
                    value = "$seed",
                    hint = "меняет только начальные позиции центров — и всё равно меняет результат",
                    textColor = textColor
                ) {
                    Slider(
                        value = seed.toFloat(), onValueChange = { seed = it.roundToInt() },
                        valueRange = KmLab.SEED_MIN.toFloat()..KmLab.SEED_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = ColorInertia, activeTrackColor = ColorInertia)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("Как ставить начальные центры", color = textColor, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in KmInit.entries) {
                        SegmentButton(m.label, initMethod == m, accent, Modifier.weight(1f)) { initMethod = m }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (initMethod == KmInit.PLUS_PLUS)
                        "Каждый следующий центр выбирается с вероятностью, пропорциональной " +
                            "квадрату расстояния до ближайшего уже выбранного. Центры расходятся, " +
                            "и алгоритм реже застревает."
                    else
                        "Центры — просто k случайных алертов. Два из них могут оказаться " +
                            "в одной и той же плотной группе, и тогда она будет разрезана, " +
                            "а какая-то другая останется без центра.",
                    color = textColor.copy(alpha = 0.55f), fontSize = 11.sp, lineHeight = 15.sp
                )

                Spacer(Modifier.height(14.dp))
                Text("Нормализация признаков", color = textColor, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegmentButton("включена", normalize, accent, Modifier.weight(1f)) { normalize = true }
                    SegmentButton("выключена", !normalize, ColorWarn, Modifier.weight(1f)) { normalize = false }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (normalize) "оба признака приведены к отрезку от нуля до единицы"
                    else "расстояния считаются в исходных единицах: хосты против минут",
                    color = textColor.copy(alpha = 0.55f), fontSize = 11.sp, lineHeight = 15.sp
                )
                if (!normalize) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Минуты меняются в диапазоне ${KmLab.MINUTES_MAX.toInt()}, хосты — " +
                            "${KmLab.HOSTS_MAX.toInt()}. В квадрат расстояния это входит " +
                            "с квадратом отношения, то есть примерно в 576 раз. Число хостов " +
                            "сейчас на кластеризацию практически не влияет: посмотрите на " +
                            "границы — они почти горизонтальные.",
                        color = ColorWarn.copy(alpha = 0.9f), fontSize = 12.sp, lineHeight = 17.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- Результат ----------------
        if (r != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Что получилось", color = textColor,
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BigStat("Силуэт", "%.3f".format(r.state.silhouette),
                            ColorSil, Modifier.weight(1f), textColor)
                        BigStat("Совпало с настоящими группами",
                            "%.1f%%".format(r.state.purity * 100),
                            accent, Modifier.weight(1f), textColor)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Инерция ${"%.4f".format(r.state.inertia)}" +
                            (if (!normalize) " (в исходных единицах — сравнивать её с " +
                                "нормализованной бессмысленно)" else ""),
                        color = textColor.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 17.sp
                    )

                    Spacer(Modifier.height(14.dp))
                    Text("Из чего состоят кластеры", color = textColor,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Настоящих групп алгоритм не видел — они показаны только вам.",
                        color = textColor.copy(alpha = 0.55f), fontSize = 11.sp, lineHeight = 15.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    for (j in 0 until k) {
                        val comp = r.compositions[j]
                        val parts = ArrayList<String>()
                        for (g in KmLab.GROUPS.indices) {
                            if (comp[g] > 0) parts.add("${KmLab.GROUPS[g].name} — ${comp[g]}")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier.padding(top = 3.dp).size(10.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(ClusterColors[j % ClusterColors.size])
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${r.state.sizes[j]} алертов: " +
                                    (if (parts.isEmpty()) "пусто" else parts.joinToString(", ")),
                                color = textColor.copy(alpha = 0.85f), fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = kmInsight(
                            k = k, iterations = iterations, seed = seed,
                            initMethod = initMethod, normalize = normalize, r = r
                        ),
                        color = textColor.copy(alpha = 0.82f), fontSize = 13.sp, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    AskChatButton(accent = accent, onClick = {
                        onOpenChat(
                            buildInteractiveChatPrompt(
                                topicTitle,
                                "кластеров = $k, шагов алгоритма = $iterations, сид = $seed, " +
                                    "инициализация — ${initMethod.label}, нормализация " +
                                    "${if (normalize) "включена" else "выключена"}",
                                "инерция = ${"%.4f".format(r.state.inertia)}, силуэт = " +
                                    "${"%.3f".format(r.state.silhouette)}, совпало с настоящими " +
                                    "группами ${"%.1f".format(r.state.purity * 100)}%, " +
                                    "силуэт максимален при k = ${r.bestK}"
                            )
                        )
                    })
                }
            }
        } else {
            Text("Считаем кластеры…", color = textColor.copy(alpha = 0.6f), fontSize = 14.sp)
        }

        Spacer(Modifier.height(8.dp))
    }
}

// =====================================================================
// Раскладка
// =====================================================================

@Composable
private fun ChartBox(height: androidx.compose.ui.unit.Dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp)),
        content = content
    )
}

@Composable
private fun LegendDot(color: Color, label: String, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = 0.85f))
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = textColor.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

@Composable
private fun SegmentButton(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) accent else Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: String,
    hint: String,
    textColor: Color,
    slider: @Composable () -> Unit
) {
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = textColor, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text(value, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
    slider()
    Text(hint, color = textColor.copy(alpha = 0.55f), fontSize = 11.sp, lineHeight = 15.sp)
}

@Composable
private fun BigStat(label: String, value: String, color: Color, modifier: Modifier, textColor: Color) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = textColor.copy(alpha = 0.7f), fontSize = 11.sp, lineHeight = 15.sp)
    }
}

// =====================================================================
// Отрисовка
// =====================================================================

@Composable
private fun ScatterCanvas(state: KmState, k: Int) {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height

        fun xOf(hosts: Double) = (hosts / KmLab.HOSTS_MAX * w).toFloat()
        fun yOf(minutes: Double) = (h - minutes / KmLab.MINUTES_MAX * h).toFloat()

        for (i in 1 until 5) {
            val gy = h * i / 5f
            drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, gy), Offset(w, gy), strokeWidth = designPx(1f))
            val gx = w * i / 5f
            drawLine(Color.White.copy(alpha = 0.07f), Offset(gx, 0f), Offset(gx, h), strokeWidth = designPx(1f))
        }

        val alerts = KmLab.alerts
        for (i in alerts.indices) {
            val c = ClusterColors[state.assignments[i] % ClusterColors.size]
            drawCircle(
                c.copy(alpha = 0.7f), radius = designPx(3.5f),
                center = Offset(xOf(alerts[i].hosts), yOf(alerts[i].minutes))
            )
        }

        for (j in 0 until k) {
            val cx = xOf(state.centroidsHosts[j])
            val cy = yOf(state.centroidsMinutes[j])
            val c = ClusterColors[j % ClusterColors.size]
            val a = designPx(9f)
            drawLine(Color.Black.copy(alpha = 0.5f), Offset(cx - a, cy - a), Offset(cx + a, cy + a),
                strokeWidth = designPx(5f))
            drawLine(Color.Black.copy(alpha = 0.5f), Offset(cx - a, cy + a), Offset(cx + a, cy - a),
                strokeWidth = designPx(5f))
            drawLine(c, Offset(cx - a, cy - a), Offset(cx + a, cy + a), strokeWidth = designPx(3f))
            drawLine(c, Offset(cx - a, cy + a), Offset(cx + a, cy - a), strokeWidth = designPx(3f))
        }

        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, h), Offset(w, h), strokeWidth = designPx(1.5f))
        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, 0f), Offset(0f, h), strokeWidth = designPx(1.5f))
    }
}

@Composable
private fun CurvesCanvas(curves: KmLab.KCurves, currentK: Int) {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height
        val n = curves.ks.size
        if (n < 2) return@Canvas

        var iLo = Double.MAX_VALUE
        var iHi = -Double.MAX_VALUE
        for (v in curves.inertia) { if (v < iLo) iLo = v; if (v > iHi) iHi = v }
        if (iHi - iLo < 1e-12) iHi = iLo + 1.0

        fun xOf(i: Int) = i.toFloat() / (n - 1) * w
        fun yInertia(v: Double) = (h - (v - iLo) / (iHi - iLo) * h * 0.92 - h * 0.04).toFloat()
        fun ySil(v: Double) = (h - v.coerceIn(0.0, 1.0) * h * 0.92 - h * 0.04).toFloat()

        for (i in 1 until 5) {
            val gy = h * i / 5f
            drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, gy), Offset(w, gy), strokeWidth = designPx(1f))
        }

        val idx = (currentK - curves.ks[0]).coerceIn(0, n - 1)
        drawLine(Color.White.copy(alpha = 0.45f), Offset(xOf(idx), 0f), Offset(xOf(idx), h),
            strokeWidth = designPx(2.5f))

        var best = 0
        for (i in curves.silhouette.indices) if (curves.silhouette[i] > curves.silhouette[best]) best = i
        drawRect(
            ColorSil.copy(alpha = 0.12f),
            topLeft = Offset(xOf(best) - designPx(11f), 0f),
            size = Size(designPx(22f), h)
        )

        fun series(values: DoubleArray, color: Color, mapper: (Double) -> Float) {
            var prev: Offset? = null
            for (i in 0 until n) {
                val pt = Offset(xOf(i), mapper(values[i]))
                prev?.let { drawLine(color, it, pt, strokeWidth = designPx(2.5f)) }
                prev = pt
            }
            for (i in 0 until n) {
                drawCircle(color, radius = designPx(3.5f), center = Offset(xOf(i), mapper(values[i])))
            }
        }

        series(curves.inertia, ColorInertia) { yInertia(it) }
        series(curves.silhouette, ColorSil) { ySil(it) }

        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, h), Offset(w, h), strokeWidth = designPx(1.5f))
    }
}

// =====================================================================
// Пояснения
// =====================================================================

private fun kmInsight(
    k: Int,
    iterations: Int,
    seed: Int,
    initMethod: KmInit,
    normalize: Boolean,
    r: KmComputed
): String {
    val parts = ArrayList<String>()

    if (!normalize) {
        parts.add(
            "Нормализация выключена, и расстояние меряется в единицах, где минуты в " +
                "двадцать четыре раза «длиннее» хостов. Совпадение с настоящими группами " +
                "${"%.1f".format(r.state.purity * 100)}%. Обратите внимание на силуэт: " +
                "${"%.3f".format(r.state.silhouette)} — он может оказаться ВЫШЕ, чем при " +
                "включённой нормализации, хотя результат хуже. Силуэт считается в том же " +
                "искажённом пространстве и потому от неверного масштаба не спасает. " +
                "Проверять масштаб приходится головой, а не метрикой."
        )
    }

    if (r.state.moved > 0) {
        parts.add(
            "Алгоритм ещё не сошёлся: на последнем шаге ${r.state.moved} алертов сменили " +
                "кластер. Добавьте шагов и посмотрите, как счётчик падает до нуля — это и " +
                "есть критерий остановки, никаких «почти сошлось» здесь не бывает."
        )
    } else if (iterations <= 8) {
        parts.add(
            "Сошлось за $iterations шагов: на последнем не переназначено ни одного алерта. " +
                "Это неподвижная точка — дальше центры не сдвинутся никогда, сколько шагов " +
                "ни добавляй."
        )
    }

    if (k < KmLab.TRUE_K) {
        parts.add(
            "Кластеров меньше, чем настоящих групп, поэтому какие-то из них слиплись. " +
                "В расшифровке ниже видно, какие именно."
        )
    } else if (k > KmLab.TRUE_K) {
        parts.add(
            "Кластеров больше, чем настоящих групп. Инерция от этого упала, но лишний " +
                "центр не нашёл новой группы — он разрезал надвое уже найденную, скорее " +
                "всего самую крупную. Метод k средних всегда стремится к кластерам " +
                "сопоставимого размера, потому что именно так минимизируется сумма " +
                "квадратов расстояний."
        )
    }

    if (initMethod == KmInit.RANDOM) {
        parts.add(
            "Инициализация случайная. Подвигайте ползунок сида: на двенадцати сидах здесь " +
                "получается шесть разных исходов, а инерция гуляет от 2.52 до 5.89 — более " +
                "чем вдвое. Это не погрешность, а разные локальные минимумы: обе фазы " +
                "алгоритма Ллойда не увеличивают инерцию, поэтому из плохого минимума " +
                "выбраться нельзя."
        )
    } else {
        parts.add(
            "При k-means++ разброс по сидам заметно меньше: три исхода вместо шести и " +
                "хорошее решение в восьми сидах из двенадцати против двух. Но гарантии " +
                "нет и здесь — поэтому на практике алгоритм запускают несколько раз и " +
                "берут прогон с наименьшей инерцией."
        )
    }

    if (r.bestK != k) {
        parts.add(
            "Силуэт при этих настройках максимален при k = ${r.bestK}, а выбрано $k."
        )
    }

    return parts.joinToString(" ")
}
