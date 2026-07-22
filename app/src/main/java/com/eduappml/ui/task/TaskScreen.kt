package com.eduappml.ui.task

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
 * Экран "Эталонная задача" (пузырь со знаком вопроса).
 * Показывает описание конкретного примера/датасета, который обучающийся
 * будет решать через интерактив, и ведёт дальше по цепочке "Мат. основа".
 */
@Composable
fun TaskScreen(
    modifier: Modifier = Modifier,
    id: String,
    title: String? = null,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val textColor = Color(0xFFF2EEFF)
    val accent = Color(0xFFFFD93D)

    var content by remember(id) { mutableStateOf<String?>(null) }
    var isLoading by remember(id) { mutableStateOf(true) }

    LaunchedEffect(id) {
        isLoading = true
        content = runCatching { InfoRepository.loadTask(context.assets, id) }.getOrNull()
        isLoading = false
    }

    LessonScaffold(
        eyebrow = "Эталонная задача",
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
            else -> NotFoundPlaceholder(id = id, section = "task", label = "Эталонная задача", textColor = textColor)
        }
    }
}
