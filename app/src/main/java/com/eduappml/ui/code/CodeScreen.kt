package com.eduappml.ui.code

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
import com.eduappml.ui.common.CodeBlockCard
import com.eduappml.ui.common.ContentBlock
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.LessonSection
import com.eduappml.ui.common.LessonSectionBlock
import com.eduappml.ui.common.NotFoundPlaceholder
import com.eduappml.ui.common.parseContentBlocks

/**
 * Экран "Программная реализация" (пузырь "< >"). Код показывается отдельными
 * карточками в стиле окна редактора (вкладка с точками, номера строк, подсветка),
 * а не обычным текстовым блоком.
 *
 * [onOpenChat] — см. аналогичный параметр в MathScreen.kt. Подключён и к
 * прозаическим блокам, и к самим карточкам кода — для последних вопрос
 * формулируется как "что делает этот код", а сам код прикладывается в виде
 * markdown code fence.
 */
@Composable
fun CodeScreen(
    modifier: Modifier = Modifier,
    id: String,
    title: String? = null,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val textColor = Color(0xFFF2EEFF)
    val accent = Color(0xFFFF914D)

    var content by remember(id) { mutableStateOf<String?>(null) }
    var isLoading by remember(id) { mutableStateOf(true) }

    LaunchedEffect(id) {
        isLoading = true
        content = runCatching { InfoRepository.loadImpl(context.assets, id) }.getOrNull()
        isLoading = false
    }

    LessonScaffold(
        eyebrow = "Код",
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
                parseContentBlocks(content!!).forEach { block ->
                    when (block) {
                        is ContentBlock.Prose -> LessonSectionBlock(
                            section = block.section,
                            textColor = textColor,
                            accent = accent,
                            onAskChat = { sec -> onOpenChat(buildCodeChatPrompt(topicTitle, sec)) }
                        )
                        is ContentBlock.Code -> CodeBlockCard(
                            block = block,
                            accent = accent,
                            onAskChat = { codeBlock -> onOpenChat(buildCodeSnippetChatPrompt(topicTitle, codeBlock)) }
                        )
                    }
                }
            }
            else -> NotFoundPlaceholder(id = id, section = "impl", label = "Программная реализация", textColor = textColor)
        }
    }
}

/** Готовый черновик вопроса для Edu.AI по разделу раздела "Код" — см. аналог в MathScreen.kt. */
private fun buildCodeChatPrompt(topicTitle: String, section: LessonSection): String {
    val maxBodyLength = 500
    val body = section.body.trim().let {
        if (it.length > maxBodyLength) it.take(maxBodyLength).trimEnd() + "…" else it
    }
    return buildString {
        append("Объясни, пожалуйста, простыми словами раздел «${section.title}» из темы «$topicTitle» (Программная реализация).")
        if (body.isNotBlank()) {
            append("\n\nВот сам раздел:\n")
            append(body)
        }
    }
}

/** Готовый черновик вопроса для Edu.AI по конкретному фрагменту кода — прикладывается как markdown code fence. */
private fun buildCodeSnippetChatPrompt(topicTitle: String, block: ContentBlock.Code): String {
    val maxCodeLength = 600
    val code = block.code.trim().let {
        if (it.length > maxCodeLength) it.take(maxCodeLength).trimEnd() + "\n…" else it
    }
    return buildString {
        append("Объясни, пожалуйста, простыми словами, что делает этот код (${block.language}) из темы «$topicTitle» (Программная реализация).")
        append("\n\n```${block.language}\n")
        append(code)
        append("\n```")
    }
}
