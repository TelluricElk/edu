package com.eduappml.ui.tr

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.common.AskChatButton
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.buildInteractiveChatPrompt
import kotlin.math.roundToInt

@Composable
fun TrInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = Color(0xFF4D96FF)
    val topicTitle = title ?: "Трансформер"

    val weights = remember { TrLab.attentionWeights() }
    var selected by remember { mutableIntStateOf(1) } // по умолчанию — "кот"

    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: "Трансформер",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.15f))) {
            Text(
                "Эмбеддинги слов здесь заданы вручную для наглядности, а не выучены на тексте — сама формула внимания при этом считается настоящая, без упрощений.",
                color = textColor.copy(alpha = 0.9f), fontSize = 12.5.sp, modifier = Modifier.padding(12.dp)
            )
        }

        Spacer(Modifier.height(14.dp))
        Text("Коснитесь слова, чтобы увидеть его внимание", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))

        FlowWords(words = TrLab.words, selected = selected, weightsFromSelected = weights[selected], onSelect = { selected = it })

        Spacer(Modifier.height(16.dp))
        Text(
            text = trInsight(selected, weights[selected]),
            color = textColor.copy(alpha = 0.75f), fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        AskChatButton(accent = accent, onClick = {
            val topIdx = weights[selected].indices.maxByOrNull { if (it == selected) -1f else weights[selected][it] } ?: selected
            onOpenChat(
                buildInteractiveChatPrompt(
                    topicTitle,
                    "выбранное слово «${TrLab.words[selected]}», вручную заданные (не обученные) эмбеддинги",
                    "сильнее всего внимание направлено на «${TrLab.words[topIdx]}» (${(weights[selected][topIdx] * 100).roundToInt()}%)"
                )
            )
        })

        Text(
            "Вес внимания от «${TrLab.words[selected]}» к каждому слову:",
            color = textColor.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
        )
        TrLab.words.forEachIndexed { idx, w ->
            val weight = weights[selected][idx]
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(w, color = textColor, fontSize = 13.sp, modifier = Modifier.width(90.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(weight.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent.copy(alpha = 0.7f))
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("${(weight * 100).roundToInt()}%", color = textColor.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Positional encoding", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        Text(
            "Точная формула из статьи — не зависит от смысла слов, только от их позиции. Каждая строка — одно слово, каждый столбец — одно измерение.",
            color = textColor.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            PositionalEncodingCanvas()
        }
    }
}

/** Живое текстовое пояснение, привязанное к выбранному слову и его главному партнёру по вниманию. */
private fun trInsight(selected: Int, weightsFromSelected: FloatArray): String {
    val words = TrLab.words
    var topIdx = selected
    var topWeight = -1f
    weightsFromSelected.forEachIndexed { idx, w ->
        if (idx != selected && w > topWeight) { topWeight = w; topIdx = idx }
    }
    val selfWeight = weightsFromSelected[selected]
    return if (topIdx == selected) {
        "«${words[selected]}» уделяет больше всего внимания самому себе (${(selfWeight * 100).roundToInt()}%) — среди остальных слов нет заметно более похожего по вручную заданным признакам."
    } else {
        "«${words[selected]}» сильнее всего смотрит на «${words[topIdx]}» (${(topWeight * 100).roundToInt()}%) — у этих двух слов более похожие векторы-признаки, чем у остальных пар в этом предложении."
    }
}

@Composable
private fun FlowWords(words: List<String>, selected: Int, weightsFromSelected: FloatArray, onSelect: (Int) -> Unit) {
    Column {
        for (rowStart in words.indices step 4) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                for (idx in rowStart until minOf(rowStart + 4, words.size)) {
                    val isSelected = idx == selected
                    val attentionFromSelected = weightsFromSelected[idx]
                    val bg = if (isSelected) Color(0xFF4D96FF).copy(alpha = 0.85f)
                             else Color.White.copy(alpha = 0.10f + attentionFromSelected * 0.6f)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .border(1.dp, Color.White.copy(alpha = if (isSelected) 0.6f else 0.2f), RoundedCornerShape(10.dp))
                            .clickable { onSelect(idx) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(words[idx], color = Color.White, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionalEncodingCanvas() {
    val pe = remember { TrLab.positionalEncoding(TrLab.words.size, TrLab.EMBED_DIM) }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val rows = pe.size
        val cols = pe[0].size
        val cellW = size.width / cols
        val cellH = size.height / rows
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val v = pe[r][c] // -1..1
                val intensity = (v + 1f) / 2f
                drawRect(
                    Color(0xFF4D96FF).copy(alpha = 0.15f + intensity * 0.7f),
                    topLeft = Offset(c * cellW, r * cellH),
                    size = androidx.compose.ui.geometry.Size(cellW - 1f, cellH - 1f)
                )
            }
        }
    }
}
