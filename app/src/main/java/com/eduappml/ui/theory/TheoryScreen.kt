package com.eduappml.ui.theory

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
import com.eduappml.ui.common.LessonSectionBlock
import com.eduappml.ui.common.NotFoundPlaceholder
import com.eduappml.ui.common.parseLessonSections

/**
 * Экран "Теория" (пузырь-книга). Полностью самостоятельный экран — не вкладка
 * внутри общего InfoScreen, а отдельный шаг в последовательности изучения темы.
 */
@Composable
fun TheoryScreen(
    modifier: Modifier = Modifier,
    id: String,
    title: String? = null,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val textColor = Color(0xFFF2EEFF)
    val accent = Color(0xFF6BCB77)

    var content by remember(id) { mutableStateOf<String?>(null) }
    var isLoading by remember(id) { mutableStateOf(true) }

    LaunchedEffect(id) {
        isLoading = true
        content = runCatching { InfoRepository.loadGeneral(context.assets, id) }.getOrNull()
        isLoading = false
    }

    LessonScaffold(
        eyebrow = "Теория",
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
                parseLessonSections(content!!).forEach { section ->
                    LessonSectionBlock(section = section, textColor = textColor, accent = accent)
                }
            }
            else -> NotFoundPlaceholder(id = id, section = "general", label = "Теория", textColor = textColor)
        }
    }
}
