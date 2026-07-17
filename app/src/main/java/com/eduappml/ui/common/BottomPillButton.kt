package com.eduappml.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Единый стиль «пилюли», как на втором/третьем экране:
 * - полупрозрачный сиреневый контейнер
 * - тонкая светлая обводка
 * - лёгкая тень
 * - без иконок — только текст
 */
@Composable
fun BottomPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = Color(0xFFB9B6FF).copy(alpha = 0.22f)
    val border = Color.White.copy(alpha = 0.35f)
    val content = Color.White

    ElevatedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = ButtonDefaults.elevatedButtonElevation(
            defaultElevation = 6.dp,
            pressedElevation = 8.dp,
            focusedElevation = 6.dp,
            hoveredElevation = 7.dp,
            disabledElevation = 0.dp
        ),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = container,
            contentColor = content
        ),
        border = BorderStroke(1.dp, SolidColor(border)),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = content
        )
    }
}