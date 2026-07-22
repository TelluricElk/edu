package com.eduappml.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Единая "рамка" для всех шести экранов подтемы (Теория / Задача / Мат. основа /
 * Код / Интерактив / Решение): небольшая цветная плашка-рубрика, крупный
 * заголовок, прокручиваемое содержимое и закреплённые снизу кнопки Назад/Далее.
 */
@Composable
fun LessonScaffold(
    eyebrow: String,
    title: String,
    onBack: () -> Unit,
    onNext: (() -> Unit)? = null,
    nextLabel: String = "Далее →",
    accent: Color = Color(0xFFB9B6FF),
    scrollable: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler { onBack() }

    val textColor = Color(0xFFF2EEFF)

    Box(modifier = modifier.fillMaxSize()) {
        val columnModifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp, bottom = 110.dp)
            .let { if (scrollable) it.verticalScroll(rememberScrollState()) else it }

        Column(modifier = columnModifier) {
            EyebrowPill(text = eyebrow, accent = accent)
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                color = textColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 18.dp)
            )
            content()
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 34.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BottomPillButton(text = "Назад", onClick = onBack)
            if (onNext != null) {
                BottomPillButton(text = nextLabel, onClick = onNext)
            }
        }
    }
}

@Composable
private fun EyebrowPill(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .background(accent.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
    }
}
