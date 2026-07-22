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
import com.eduappml.ui.common.LessonSectionBlock
import com.eduappml.ui.common.NotFoundPlaceholder
import com.eduappml.ui.common.parseContentBlocks

/**
 * Экран "Программная реализация" (пузырь "< >"). Код показывается отдельными
 * карточками в стиле окна редактора (вкладка с точками, номера строк, подсветка),
 * а не обычным текстовым блоком.
 */
@Composable
fun CodeScreen(
    modifier: Modifier = Modifier,
    id: String,
    title: String? = null,
    onBack: () -> Unit,
    onNext: () -> Unit
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
                parseContentBlocks(content!!).forEach { block ->
                    when (block) {
                        is ContentBlock.Prose -> LessonSectionBlock(
                            section = block.section,
                            textColor = textColor,
                            accent = accent
                        )
                        is ContentBlock.Code -> CodeBlockCard(block = block)
                    }
                }
            }
            else -> NotFoundPlaceholder(id = id, section = "impl", label = "Программная реализация", textColor = textColor)
        }
    }
}
