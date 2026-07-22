package com.eduappml.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Один смысловой блок текста: необязательный заголовок + тело (markdown-инлайны —
 * **жирный**, списки, $формулы$ — сохраняются и рендерятся через [MarkdownText]).
 * Заголовки и декоративные "---" сюда уже не попадают — они превращаются
 * в нативные Compose-элементы на этапе парсинга.
 */
data class LessonSection(
    val level: Int,      // 1 — крупный заголовок темы, 2 — раздел, 3 — подраздел, 0 — без заголовка
    val title: String,
    val body: String
)

/**
 * Разбирает markdown-текст ассетов (general/math/task) на список [LessonSection],
 * убирая "#", "##", "###" и горизонтальные разделители "---" — вместо них
 * заголовки рисуются как обычные Compose Text с нужным размером/начертанием.
 */
fun parseLessonSections(markdown: String): List<LessonSection> {
    val lines = markdown.lines()
    val sections = mutableListOf<LessonSection>()
    var title: String? = null
    var level = 0
    val body = StringBuilder()

    fun flush() {
        val bodyText = body.toString().trim()
        if (!title.isNullOrBlank() || bodyText.isNotBlank()) {
            sections.add(LessonSection(level, title.orEmpty(), bodyText))
        }
        body.clear()
    }

    for (raw in lines) {
        val trimmed = raw.trim()
        when {
            trimmed.isEmpty() && body.isEmpty() && title == null -> Unit
            trimmed == "---" || trimmed.matches(Regex("^-{3,}$")) -> Unit
            trimmed.startsWith("#### ") -> {
                flush(); title = trimmed.removePrefix("#### ").trim(); level = 4
            }
            trimmed.startsWith("### ") -> {
                flush(); title = trimmed.removePrefix("### ").trim(); level = 3
            }
            trimmed.startsWith("## ") -> {
                flush(); title = trimmed.removePrefix("## ").trim(); level = 2
            }
            trimmed.startsWith("# ") -> {
                flush(); title = trimmed.removePrefix("# ").trim(); level = 1
            }
            else -> body.appendLine(raw)
        }
    }
    flush()
    return sections
}

/** Цветной акцентный маркер + заголовок раздела + markdown-тело. */
@Composable
fun LessonSectionBlock(
    section: LessonSection,
    textColor: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        if (section.title.isNotBlank()) {
            when (section.level) {
                1 -> {
                    Text2(
                        text = section.title,
                        color = textColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(14.dp))
                }
                2 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(
                            modifier = Modifier
                                .width(4.dp)
                                .height(18.dp)
                                .background(accent, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text2(
                            text = section.title,
                            color = textColor,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                3 -> {
                    Text2(
                        text = section.title,
                        color = accent,
                        fontSize = 15.5f.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                }
                else -> {
                    Text2(
                        text = section.title,
                        color = textColor.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        if (section.body.isNotBlank()) {
            MarkdownText(
                markdown = section.body,
                textColor = textColor.copy(alpha = 0.88f),
                textSizeSp = 16f
            )
        }
    }
}

/** Единая заглушка "раздел ещё не готов" — используется во всех шести экранах темы. */
@Composable
fun NotFoundPlaceholder(
    id: String,
    section: String,
    label: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 24.dp)) {
        androidx.compose.material3.Text(
            text = "«$label» пока готовится",
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.Text(
            text = "Для темы «$id» этот раздел ещё не заполнен. Ожидается файл info/classic/$id/$section.ru.md.",
            color = textColor.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
    }
}

/** Небольшая обёртка над material3 Text, чтобы не тащить лишний импорт в каждый файл. */
@Composable
private fun Text2(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight
) {
    androidx.compose.material3.Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight
    )
}
