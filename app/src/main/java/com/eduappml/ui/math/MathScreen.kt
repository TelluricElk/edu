package com.eduappml.ui.math

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eduappml.data.InfoRepository
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.LessonSection
import com.eduappml.ui.common.LessonSectionBlock
import com.eduappml.ui.common.NotFoundPlaceholder
import com.eduappml.ui.common.parseLessonSections

/**
 * Экран "Мат. основа" (пузырь-"S"). Грузит math.ru.md и рендерит через
 * LessonSectionBlock -> MarkdownText, формулы — через JLatexMathPlugin
 * (блочный синтаксис "$$" на отдельных строках, см. MarkdownText.kt).
 *
 * [onOpenChat] — колбэк для кнопки "Объяснить в чате" под каждым разделом
 * (кроме самого первого, общего заголовка темы): получает готовый текст
 * вопроса, предзаполняет им поле ввода в ChatScreen, но НЕ отправляет
 * автоматически — пользователь видит черновик и сам решает, отправлять
 * как есть или поправить.
 */
@Composable
fun MathScreen(
    modifier: Modifier = Modifier,
    id: String,
    title: String? = null,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val textColor = Color(0xFFF2EEFF)
    val accent = Color(0xFF4D96FF)

    var content by remember(id) { mutableStateOf<String?>(null) }
    var isLoading by remember(id) { mutableStateOf(true) }

    LaunchedEffect(id) {
        isLoading = true
        content = runCatching { InfoRepository.loadMath(context.assets, id) }.getOrNull()
        isLoading = false
    }

    LessonScaffold(
        eyebrow = "Мат. основа",
        title = title ?: id,
        onBack = onBack,
        onNext = onNext,
        accent = accent,
        modifier = modifier
    ) {
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                CircularProgressIndicator(color = textColor)
            }
            content != null -> {
                val topicTitle = title ?: id
                parseLessonSections(content!!).forEach { section ->
                    LessonSectionBlock(
                        section = section,
                        textColor = textColor,
                        accent = accent,
                        onAskChat = { sec -> onOpenChat(buildMathChatPrompt(topicTitle, sec)) }
                    )
                }
            }
            else -> NotFoundPlaceholder(id = id, section = "math", label = "Мат. основа", textColor = textColor)
        }
    }
}

/**
 * Готовый черновик вопроса для Edu.AI — название темы, название раздела и
 * сам текст раздела (обрезанный до разумной длины), чтобы чат отвечал про
 * именно ЭТУ формулу теми же обозначениями, что уже видел пользователь,
 * а не давал общий учебниковый ответ, который может разойтись с текстом.
 */
private fun buildMathChatPrompt(topicTitle: String, section: LessonSection): String {
    val maxBodyLength = 500
    val body = section.body.trim().let {
        if (it.length > maxBodyLength) it.take(maxBodyLength).trimEnd() + "…" else it
    }
    return buildString {
        append("Объясни, пожалуйста, простыми словами раздел «${section.title}» из темы «$topicTitle» (Мат. основа).")
        if (body.isNotBlank()) {
            append("\n\nВот сам раздел:\n")
            append(body)
        }
    }
}
