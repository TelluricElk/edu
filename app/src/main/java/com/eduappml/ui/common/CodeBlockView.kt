package com.eduappml.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Прозаический блок или блок кода — то, из чего состоит "Программная реализация". */
sealed class ContentBlock {
    data class Prose(val section: LessonSection) : ContentBlock()
    data class Code(val language: String, val code: String) : ContentBlock()
}

/**
 * Разбирает markdown с ``` code fences ``` на чередующиеся блоки текста и кода.
 * Заголовки внутри прозаических кусков обрабатываются так же, как в [parseLessonSections].
 */
fun parseContentBlocks(markdown: String): List<ContentBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<ContentBlock>()

    var title: String? = null
    var level = 0
    val body = StringBuilder()

    fun flushProse() {
        val bodyText = body.toString().trim()
        if (!title.isNullOrBlank() || bodyText.isNotBlank()) {
            blocks.add(ContentBlock.Prose(LessonSection(level, title.orEmpty(), bodyText)))
        }
        body.clear()
        title = null
    }

    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val trimmed = raw.trim()
        when {
            trimmed.startsWith("```") -> {
                flushProse()
                val lang = trimmed.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i]); i++
                }
                blocks.add(ContentBlock.Code(lang.ifBlank { "kotlin" }, codeLines.joinToString("\n")))
            }
            trimmed == "---" -> Unit
            trimmed.startsWith("#### ") -> { flushProse(); title = trimmed.removePrefix("#### ").trim(); level = 4 }
            trimmed.startsWith("### ") -> { flushProse(); title = trimmed.removePrefix("### ").trim(); level = 3 }
            trimmed.startsWith("## ") -> { flushProse(); title = trimmed.removePrefix("## ").trim(); level = 2 }
            trimmed.startsWith("# ") -> { flushProse(); title = trimmed.removePrefix("# ").trim(); level = 1 }
            else -> body.appendLine(raw)
        }
        i++
    }
    flushProse()
    return blocks
}

private val KOTLIN_KEYWORDS = setOf(
    "fun", "val", "var", "class", "data", "enum", "object", "interface", "when", "if", "else",
    "return", "private", "public", "internal", "protected", "override", "companion", "import",
    "package", "for", "while", "in", "is", "as", "null", "true", "false", "this", "super",
    "try", "catch", "finally", "throw", "typealias", "sealed", "abstract", "open", "const",
    "lateinit", "suspend", "inline", "reified", "out", "by", "init", "constructor", "vararg"
)

private val PYTHON_KEYWORDS = setOf(
    "def", "class", "import", "from", "as", "return", "if", "elif", "else", "for", "while",
    "in", "is", "not", "and", "or", "None", "True", "False", "self", "lambda", "try", "except",
    "finally", "raise", "with", "yield", "global", "nonlocal", "pass", "break", "continue",
    "assert", "async", "await", "del", "print"
)

private fun keywordsFor(language: String): Set<String> = when (language.trim().lowercase()) {
    "python", "py" -> PYTHON_KEYWORDS
    else -> KOTLIN_KEYWORDS
}

private val CODE_BG = Color(0xFF1E1E2E)
private val CODE_KEYWORD = Color(0xFFC792EA)
private val CODE_STRING = Color(0xFFC3E88D)
private val CODE_COMMENT = Color(0xFF6A737D)
private val CODE_NUMBER = Color(0xFFF78C6C)
private val CODE_TYPE = Color(0xFF82AAFF)
private val CODE_DEFAULT = Color(0xFFE4E4EF)

private val TOKEN_REGEX = Regex(
    """(//[^\n]*|\#[^\n]*)|("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')|(\b\d+(?:\.\d+)?[fFlL]?\b)|([A-Za-z_][A-Za-z0-9_]*)|(.)""",
    RegexOption.DOT_MATCHES_ALL
)

/** Очень лёгкая, "достаточно хорошая" подсветка синтаксиса (Kotlin/Python) без внешних библиотек. */
private fun highlightCodeLine(code: String, language: String) = buildAnnotatedString {
    val keywords = keywordsFor(language)
    for (m in TOKEN_REGEX.findAll(code)) {
        val g = m.value
        when {
            m.groups[1] != null -> withStyle(SpanStyle(color = CODE_COMMENT)) { append(g) }
            m.groups[2] != null -> withStyle(SpanStyle(color = CODE_STRING)) { append(g) }
            m.groups[3] != null -> withStyle(SpanStyle(color = CODE_NUMBER)) { append(g) }
            m.groups[4] != null -> {
                val style = when {
                    g in keywords -> SpanStyle(color = CODE_KEYWORD, fontWeight = FontWeight.SemiBold)
                    g.firstOrNull()?.isUpperCase() == true -> SpanStyle(color = CODE_TYPE)
                    else -> SpanStyle(color = CODE_DEFAULT)
                }
                withStyle(style) { append(g) }
            }
            else -> withStyle(SpanStyle(color = CODE_DEFAULT)) { append(g) }
        }
    }
}

/** Карточка кода, оформленная как окно редактора: вкладка с точками, гаттер номеров строк, подсветка. */
@Composable
fun CodeBlockCard(block: ContentBlock.Code, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CODE_BG)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Dot(Color(0xFFFF5F56))
            Spacer(Modifier.width(6.dp))
            Dot(Color(0xFFFFBD2E))
            Spacer(Modifier.width(6.dp))
            Dot(Color(0xFF27C93F))
            Spacer(Modifier.weight(1f))
            Text(
                text = block.language,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        val lines = remember(block.code) { block.code.trimEnd('\n').split("\n") }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.padding(end = 14.dp)) {
                lines.indices.forEach { idx ->
                    Text(
                        text = "${idx + 1}",
                        color = Color.White.copy(alpha = 0.28f),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(22.dp)
                    )
                }
            }
            Column {
                lines.forEach { line ->
                    Text(
                        text = highlightCodeLine(line, block.language),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier.size(10.dp).clip(CircleShape).background(color)
    )
}
