package com.eduappml.ui.rf

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

private val ColorMalware = Color(0xFFFF6B6B)
private val ColorClean = Color(0xFF6BCB77)
private val ColorAccent = Color(0xFFFFD93D)
private val ColorOob = Color(0xFF9AA7FF)
private val ColorSingle = Color(0xFFB0B6C4)

/** Всё, что считается в фоне за один заход. */
private class RfComputed(
    val singleTrainAcc: Double,
    val singleTestAcc: Double,
    val singleLeaves: Int,
    val forestTrainAcc: Double,
    val forestTestAcc: Double,
    val oobAcc: Double,
    val curve: RfLab.ForestCurve,
    val importance: DoubleArray,
    val histogram: Array<IntArray>
)

/**
 * Интерактив темы «Случайный лес»: статический детект ВПО по признакам
 * PE-файла.
 *
 * Центральный элемент экрана — кривая точности по числу деревьев с
 * горизонтальной линией «одно дерево той же глубины». Расстояние между
 * кривой и линией — это ровно та польза, которую даёт усреднение, и оно
 * растёт, когда деревьям разрешают быть глубже.
 *
 * Второй элемент — важности признаков по всему лесу: то, чего с одного
 * дерева не увидеть.
 *
 * Третий — распределение долей голосов. Лес отвечает не «да/нет», а
 * «двадцать деревьев из двадцати пяти», и по ширине разброса видно, где
 * модель уверена, а где спорит сама с собой.
 */
@Composable
fun RfInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = ColorAccent
    val topicTitle = title ?: "Случайный лес"

    var nTrees by remember { mutableIntStateOf(RfLab.DEFAULT_TREES) }
    var depth by remember { mutableIntStateOf(RfLab.DEFAULT_DEPTH) }
    var mtry by remember { mutableIntStateOf(RfLab.DEFAULT_MTRY) }
    var minSplit by remember { mutableIntStateOf(RfLab.DEFAULT_MIN_SPLIT) }
    var noise by remember { mutableFloatStateOf(RfLab.DEFAULT_NOISE.toFloat()) }
    var bootstrap by remember { mutableStateOf(RfLab.DEFAULT_BOOTSTRAP) }
    var criterion by remember { mutableStateOf(RfLab.DEFAULT_CRITERION) }

    val train = remember(noise) { RfLab.trainSet(noise.toDouble()) }
    val test = remember(noise) { RfLab.testSet(noise.toDouble()) }

    var computed by remember { mutableStateOf<RfComputed?>(null) }

    LaunchedEffect(nTrees, depth, mtry, minSplit, noise, bootstrap, criterion) {
        delay(180)
        computed = withContext(Dispatchers.Default) {
            val single = RfLab.buildSingleTree(train, criterion, depth, minSplit)
            val forest = RfLab.trainForest(
                train = train,
                criterion = criterion,
                nTrees = nTrees,
                maxDepth = depth,
                minSamplesSplit = minSplit,
                mtry = mtry,
                bootstrap = bootstrap
            )
            val curve = RfLab.forestCurve(forest, train, test)
            RfComputed(
                singleTrainAcc = RfLab.accuracy(single, train),
                singleTestAcc = RfLab.accuracy(single, test),
                singleLeaves = RfLab.leafCount(single),
                forestTrainAcc = RfLab.forestAccuracy(forest, train),
                forestTestAcc = RfLab.forestAccuracy(forest, test),
                oobAcc = if (curve.oobCurve.isEmpty()) 0.0 else curve.oobCurve[curve.oobCurve.size - 1],
                curve = curve,
                importance = RfLab.importance(forest),
                histogram = RfLab.voteHistogram(forest, test)
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
            "Коллекция из ${RfLab.TRAIN_SIZE} размеченных PE-файлов, контроль — ${RfLab.TEST_SIZE}. " +
                "Шесть признаков извлекаются из файла без запуска: энтропия кода, таблица импорта, " +
                "структура секций, оверлей, подпись, строки. Задача — собрать детектор, который " +
                "не развалится на файлах, которых не видел.",
            fontSize = 14.sp, color = textColor.copy(alpha = 0.75f), lineHeight = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ---------------- Главный график ----------------
        Text(
            "Точность по числу деревьев",
            color = textColor.copy(alpha = 0.9f), fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)
        )
        ChartBox(height = 210.dp) {
            if (r != null) ForestCurveCanvas(r.curve, r.singleTestAcc)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendDot(ColorAccent, "лес, контрольная", textColor)
            LegendDot(ColorOob, "OOB-оценка", textColor)
            LegendDot(ColorSingle, "одно дерево", textColor)
        }
        Text(
            "Серая линия — одиночное дерево той же предельной глубины. Всё, что выше неё, " +
                "получено даром: код дерева не менялся, добавилось только усреднение. " +
                "OOB-оценка посчитана вообще без контрольной выборки — по тем файлам, " +
                "которых каждое дерево не видело.",
            fontSize = 12.sp, color = textColor.copy(alpha = 0.6f), lineHeight = 17.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )

        // ---------------- Важности ----------------
        Text(
            "На что лес опирается",
            color = textColor.copy(alpha = 0.9f), fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)
        )
        if (r != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                var top = 0
                for (i in r.importance.indices) if (r.importance[i] > r.importance[top]) top = i
                for (i in 0 until RfLab.N_FEAT) {
                    ImportanceRow(
                        label = RfLab.featureNames[i],
                        value = r.importance[i],
                        maxValue = r.importance[top],
                        highlighted = i == top,
                        textColor = textColor
                    )
                }
            }
        }
        Text(
            "Важность — суммарный размер узлов, где лес разбивал по этому признаку. " +
                "По одному дереву её считать бессмысленно: там корневой признак заслоняет " +
                "остальные. Усреднение по деревьям и делает картину честной.",
            fontSize = 12.sp, color = textColor.copy(alpha = 0.6f), lineHeight = 17.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 18.dp)
        )

        // ---------------- Голосование ----------------
        Text(
            "Как распределились голоса",
            color = textColor.copy(alpha = 0.9f), fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)
        )
        ChartBox(height = 190.dp) {
            if (r != null) VoteHistogramCanvas(r.histogram)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendDot(ColorClean, "на самом деле чистые", textColor)
            LegendDot(ColorMalware, "на самом деле вредоносные", textColor)
        }
        Text(
            "По горизонтали — доля деревьев, сказавших «вредоносный», от 0 до 1. " +
                "Белая черта посередине — порог решения. Столбцы у краёв означают единодушие, " +
                "столбцы у черты — файлы, на которых лес спорит сам с собой: именно их и стоит " +
                "отдавать аналитику вручную.",
            fontSize = 12.sp, color = textColor.copy(alpha = 0.6f), lineHeight = 17.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )

        // ---------------- Разнообразие деревьев ----------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Откуда берётся разнообразие", color = textColor,
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Источников ровно два. Выключите оба — и все деревья станут " +
                        "буквально одинаковыми.",
                    color = textColor.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 17.sp
                )

                SliderRow(
                    label = "Деревьев в лесу",
                    value = "$nTrees",
                    hint = when {
                        nTrees <= 3 -> "слишком мало: усреднять нечего, результат скачет"
                        nTrees >= 35 -> "плато: кривая уже давно выровнялась, время растёт впустую"
                        else -> "рабочая область"
                    },
                    textColor = textColor
                ) {
                    Slider(
                        value = nTrees.toFloat(), onValueChange = { nTrees = it.roundToInt() },
                        valueRange = RfLab.TREES_MIN.toFloat()..RfLab.TREES_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )
                }

                SliderRow(
                    label = "Признаков на узел",
                    value = if (mtry >= RfLab.N_FEAT) "все ${RfLab.N_FEAT}" else "$mtry из ${RfLab.N_FEAT}",
                    hint = when {
                        mtry >= RfLab.N_FEAT -> "второй источник разнообразия выключен"
                        mtry == 1 -> "разрез выбирается почти вслепую — деревья слабеют"
                        else -> "каждый узел видит только часть признаков"
                    },
                    textColor = textColor
                ) {
                    Slider(
                        value = mtry.toFloat(), onValueChange = { mtry = it.roundToInt() },
                        valueRange = RfLab.MTRY_MIN.toFloat()..RfLab.MTRY_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = ColorOob, activeTrackColor = ColorOob)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("Бутстрап-выборки", color = textColor, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegmentButton("включены", bootstrap, accent, Modifier.weight(1f)) { bootstrap = true }
                    SegmentButton("выключены", !bootstrap, ColorMalware, Modifier.weight(1f)) { bootstrap = false }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (bootstrap) "каждое дерево учится на своей выборке с возвратом"
                    else "все деревья видят одну и ту же выборку целиком",
                    color = textColor.copy(alpha = 0.55f), fontSize = 11.sp, lineHeight = 15.sp
                )
                if (!bootstrap && mtry >= RfLab.N_FEAT) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Оба источника выключены: все $nTrees деревьев построены по одним и тем же " +
                            "данным одним и тем же детерминированным алгоритмом, то есть совпадают " +
                            "до последнего узла. Лес сейчас — это одно дерево, переписанное " +
                            "$nTrees раз. Обратите внимание, что жёлтая кривая легла на серую линию.",
                        color = ColorMalware.copy(alpha = 0.9f), fontSize = 12.sp, lineHeight = 17.sp
                    )
                }
                if (!bootstrap && mtry < RfLab.N_FEAT) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Бутстрап выключен, но подпространство признаков осталось — и одного этого " +
                            "уже хватает, чтобы деревья различались. Два механизма во многом " +
                            "взаимозаменяемы.",
                        color = textColor.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 17.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- Каким деревьям расти ----------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Каким деревьям расти", color = textColor,
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                SliderRow(
                    label = "Предельная глубина",
                    value = "$depth",
                    hint = when {
                        depth <= 2 -> "деревья слишком грубые: усреднять почти нечего"
                        depth >= 10 -> "одиночное дерево здесь заучивает выборку — а лес нет"
                        else -> "рабочая область"
                    },
                    textColor = textColor
                ) {
                    Slider(
                        value = depth.toFloat(), onValueChange = { depth = it.roundToInt() },
                        valueRange = RfLab.DEPTH_MIN.toFloat()..RfLab.DEPTH_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )
                }

                SliderRow(
                    label = "Минимум файлов для разбиения",
                    value = "$minSplit",
                    hint = if (minSplit <= 4)
                        "узлы дробятся до последнего — ровно то, что лесу и нужно"
                    else "деревья подрезаны заранее, и лес теряет часть своего смысла",
                    textColor = textColor
                ) {
                    Slider(
                        value = minSplit.toFloat(), onValueChange = { minSplit = it.roundToInt() },
                        valueRange = RfLab.MIN_SPLIT_MIN.toFloat()..RfLab.MIN_SPLIT_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                    )
                }

                SliderRow(
                    label = "Шум разметки: аналитик ошибся",
                    value = "${(noise * 100).roundToInt()}%",
                    hint = if (noise < 0.02f)
                        "все вердикты верны — разрыв между деревом и лесом почти исчезает"
                    else "часть файлов размечена неверно, как в настоящей коллекции",
                    textColor = textColor
                ) {
                    Slider(
                        value = noise, onValueChange = { noise = it },
                        valueRange = RfLab.NOISE_MIN.toFloat()..RfLab.NOISE_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = ColorMalware, activeTrackColor = ColorMalware)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("Критерий неоднородности", color = textColor, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (cv in RfCriterion.entries) {
                        SegmentButton(cv.label, criterion == cv, accent, Modifier.weight(1f)) { criterion = cv }
                    }
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
                        BigStat("Одно дерево, контрольная", "%.3f".format(r.singleTestAcc),
                            ColorSingle, Modifier.weight(1f), textColor)
                        BigStat("Лес, контрольная", "%.3f".format(r.forestTestAcc),
                            ColorAccent, Modifier.weight(1f), textColor)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BigStat("OOB — без контрольной выборки",
                            if (bootstrap) "%.3f".format(r.oobAcc) else "нет",
                            ColorOob, Modifier.weight(1f), textColor)
                        BigStat("Выигрыш леса",
                            "%+.3f".format(r.forestTestAcc - r.singleTestAcc),
                            if (r.forestTestAcc >= r.singleTestAcc) ColorClean else ColorMalware,
                            Modifier.weight(1f), textColor)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Одно дерево на обучающей: ${"%.3f".format(r.singleTrainAcc)} " +
                            "(листьев ${r.singleLeaves})   •   лес на обучающей: " +
                            "${"%.3f".format(r.forestTrainAcc)}",
                        color = textColor.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 17.sp
                    )
                    if (!bootstrap) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "OOB не определён: без бутстрапа у деревьев нет невиданных файлов.",
                            color = textColor.copy(alpha = 0.55f), fontSize = 11.sp, lineHeight = 15.sp
                        )
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
                        text = rfInsight(
                            nTrees = nTrees, depth = depth, mtry = mtry, minSplit = minSplit,
                            noise = noise.toDouble(), bootstrap = bootstrap, criterion = criterion,
                            singleTestAcc = r.singleTestAcc, singleTrainAcc = r.singleTrainAcc,
                            forestTestAcc = r.forestTestAcc, oobAcc = r.oobAcc, curve = r.curve
                        ),
                        color = textColor.copy(alpha = 0.82f), fontSize = 13.sp, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    AskChatButton(accent = accent, onClick = {
                        onOpenChat(
                            buildInteractiveChatPrompt(
                                topicTitle,
                                "деревьев = $nTrees, предельная глубина = $depth, " +
                                    "признаков на узел = $mtry из ${RfLab.N_FEAT}, " +
                                    "минимум файлов для разбиения = $minSplit, " +
                                    "бутстрап ${if (bootstrap) "включён" else "выключен"}, " +
                                    "критерий — ${criterion.label}, " +
                                    "шум разметки = ${(noise * 100).roundToInt()}%",
                                "одно дерево на контрольной = ${"%.3f".format(r.singleTestAcc)}, " +
                                    "лес на контрольной = ${"%.3f".format(r.forestTestAcc)}, " +
                                    "OOB = ${if (bootstrap) "%.3f".format(r.oobAcc) else "не определён"}"
                            )
                        )
                    })
                }
            }
        } else {
            Text("Растёт лес…", color = textColor.copy(alpha = 0.6f), fontSize = 14.sp)
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
private fun ImportanceRow(
    label: String,
    value: Double,
    maxValue: Double,
    highlighted: Boolean,
    textColor: Color
) {
    val share = if (maxValue > 0.0) (value / maxValue).toFloat() else 0f
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                color = textColor.copy(alpha = if (highlighted) 0.95f else 0.7f),
                fontSize = 12.sp,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Text(
                "%.3f".format(value),
                color = if (highlighted) ColorAccent else textColor.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(share.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background((if (highlighted) ColorAccent else ColorOob).copy(alpha = 0.75f))
            )
        }
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
private fun ForestCurveCanvas(curve: RfLab.ForestCurve, singleAcc: Double) {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height
        val n = curve.testCurve.size
        if (n < 1) return@Canvas

        val lo = 0.5
        val hi = 1.0
        fun yOf(v: Double) = (h - ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0) * h).toFloat()
        fun xOf(i: Int) = if (n == 1) w / 2f else i.toFloat() / (n - 1) * w

        for (k in 1 until 5) {
            val gy = h * k / 5f
            drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, gy), Offset(w, gy), strokeWidth = designPx(1f))
        }

        // одиночное дерево — горизонтальная опорная линия
        val sy = yOf(singleAcc)
        var x = 0f
        while (x < w) {
            drawLine(
                ColorSingle.copy(alpha = 0.8f),
                Offset(x, sy), Offset(minOf(x + designPx(7f), w), sy),
                strokeWidth = designPx(2f)
            )
            x += designPx(13f)
        }

        fun drawSeries(values: DoubleArray, color: Color) {
            var prev: Offset? = null
            for (i in 0 until n) {
                val pt = Offset(xOf(i), yOf(values[i]))
                prev?.let { drawLine(color, it, pt, strokeWidth = designPx(2.5f)) }
                prev = pt
            }
            if (n <= 26) {
                for (i in 0 until n) {
                    drawCircle(color, radius = designPx(2.5f), center = Offset(xOf(i), yOf(values[i])))
                }
            }
            drawCircle(color, radius = designPx(4.5f), center = Offset(xOf(n - 1), yOf(values[n - 1])))
        }

        var hasOob = false
        for (v in curve.oobCurve) if (v > 0.0) { hasOob = true; break }
        if (hasOob) drawSeries(curve.oobCurve, ColorOob)
        drawSeries(curve.testCurve, ColorAccent)

        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, h), Offset(w, h), strokeWidth = designPx(1.5f))
    }
}

@Composable
private fun VoteHistogramCanvas(hist: Array<IntArray>) {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height
        val bins = hist[0].size
        var top = 1
        for (b in 0 until bins) {
            val s = hist[0][b] + hist[1][b]
            if (s > top) top = s
        }
        val bw = w / bins

        for (b in 0 until bins) {
            val clean = hist[0][b]
            val mal = hist[1][b]
            val cleanH = clean.toFloat() / top * h
            val malH = mal.toFloat() / top * h
            drawRect(
                color = ColorClean.copy(alpha = 0.7f),
                topLeft = Offset(b * bw + bw * 0.12f, h - cleanH),
                size = Size(bw * 0.76f, cleanH)
            )
            drawRect(
                color = ColorMalware.copy(alpha = 0.7f),
                topLeft = Offset(b * bw + bw * 0.12f, h - cleanH - malH),
                size = Size(bw * 0.76f, malH)
            )
        }

        drawLine(
            Color.White.copy(alpha = 0.7f),
            Offset(w / 2f, 0f), Offset(w / 2f, h),
            strokeWidth = designPx(2f)
        )
        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, h), Offset(w, h), strokeWidth = designPx(1.5f))
    }
}

// =====================================================================
// Пояснения
// =====================================================================

private fun rfInsight(
    nTrees: Int,
    depth: Int,
    mtry: Int,
    minSplit: Int,
    noise: Double,
    bootstrap: Boolean,
    criterion: RfCriterion,
    singleTestAcc: Double,
    singleTrainAcc: Double,
    forestTestAcc: Double,
    oobAcc: Double,
    curve: RfLab.ForestCurve
): String {
    val parts = ArrayList<String>()
    val gain = forestTestAcc - singleTestAcc

    if (!bootstrap && mtry >= RfLab.N_FEAT) {
        parts.add(
            "Разнообразия нет ни по данным, ни по признакам, поэтому все $nTrees деревьев " +
                "совпадают, и лес честно показывает точность одного дерева " +
                "(${"%.3f".format(forestTestAcc)}). Это не поломка, а определение: ансамбль из " +
                "одинаковых моделей равен одной модели. Включите бутстрап или уменьшите число " +
                "признаков на узел — и кривая оторвётся от серой линии."
        )
        return parts.joinToString(" ")
    }

    if (depth >= 8) {
        parts.add(
            "Глубина $depth: одиночное дерево заучило коллекцию (на обучающей " +
                "${"%.3f".format(singleTrainAcc)}, на контрольной всего " +
                "${"%.3f".format(singleTestAcc)}). Лес из таких же переученных деревьев даёт " +
                "${"%.3f".format(forestTestAcc)}. Именно в этом смысл бэггинга: он не мешает " +
                "деревьям ошибаться, он делает так, чтобы они ошибались по-разному и их " +
                "ошибки гасили друг друга при усреднении."
        )
    } else if (depth <= 3) {
        parts.add(
            "Глубина $depth даёт слабые деревья, и усреднять почти нечего: разница с одиночным " +
                "деревом ${"%+.3f".format(gain)}. Бэггинг убирает разброс, но не смещение — " +
                "если все деревья систематически недоучены, лес недоучен ровно так же. " +
                "Поднимите глубину и посмотрите, как разрыв начнёт расти."
        )
    } else {
        parts.add(
            "На глубине $depth лес даёт ${"%.3f".format(forestTestAcc)} против " +
                "${"%.3f".format(singleTestAcc)} у одиночного дерева (${"%+.3f".format(gain)})."
        )
    }

    if (nTrees <= 4) {
        parts.add(
            "Деревьев всего $nTrees — усреднение ещё не работает, и результат скачет от " +
                "добавления каждого следующего. Это видно по левому краю кривой."
        )
    } else if (nTrees >= 30) {
        val mid = curve.testCurve[minOf(11, curve.testCurve.size - 1)]
        parts.add(
            "После примерно дюжины деревьев кривая выходит на плато: на 12 деревьях было " +
                "${"%.3f".format(mid)}, сейчас на $nTrees — ${"%.3f".format(forestTestAcc)}. " +
                "Лишние деревья не вредят, но и не помогают — они просто стоят времени."
        )
    }

    if (bootstrap) {
        parts.add(
            "OOB-оценка ${"%.3f".format(oobAcc)} против контрольной " +
                "${"%.3f".format(forestTestAcc)}: разница ${"%.3f".format(kotlin.math.abs(oobAcc - forestTestAcc))}. " +
                "OOB получена бесплатно — по тем файлам, которые каждое дерево не видело в своей " +
                "бутстрап-выборке, а таких примерно треть коллекции."
        )
    }

    if (mtry == 1) {
        parts.add(
            "Один признак на узел — деревья выбирают разрез почти вслепую и поодиночке слабы. " +
                "Лес это частично вытягивает, но обычно разумнее брать 2–3 признака."
        )
    }

    if (minSplit >= 16) {
        parts.add(
            "Минимум $minSplit файлов на разбиение подрезает деревья заранее. Для одиночного " +
                "дерева это лекарство, а лесу оно скорее мешает: ему нужны именно глубокие, " +
                "разнообразные, каждое по-своему переученные деревья."
        )
    }

    if (noise < 0.02) {
        parts.add(
            "Разметка чистая, и разрыв между деревом и лесом почти исчез: переобучаться " +
                "не на чем. Верните шум — разница вернётся вместе с ним."
        )
    } else if (noise > 0.25) {
        parts.add(
            "При ${(noise * 100).roundToInt()}% неверных вердиктов обе модели идут вниз: " +
                "усреднение гасит разброс, но не восстанавливает разметку, которой не было."
        )
    }

    if (criterion == RfCriterion.ENTROPY) {
        parts.add(
            "Критерий энтропии даёт лес, похожий на построенный по Джини, но не идентичный. " +
                "Разница между критериями обычно невелика, и это честный результат."
        )
    }

    return parts.joinToString(" ")
}
