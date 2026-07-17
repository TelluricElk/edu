package com.eduappml.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun FormulaDisplay(
    formula: String,
    textColor: Color,
    textSizeSp: Float = 16f,
    formulaScale: Float = 1.4f
) {
    val alreadyWrapped = formula.contains('$') ||
            formula.contains("\\(") || formula.contains("\\)") ||
            formula.contains("\\[") || formula.contains("\\]")

    val md = if (alreadyWrapped) formula else "$$${formula.trim()}$$"

    MarkdownText(
        markdown = md,
        textColor = textColor,
        textSizeSp = textSizeSp,
        formulaScale = formulaScale
    )
}