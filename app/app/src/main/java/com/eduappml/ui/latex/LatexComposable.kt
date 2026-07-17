package com.eduappml.ui.latex

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXIcon
import kotlin.math.min

/**
 * Рендер LaTeX-формулы в Bitmap и показ в Compose.
 *
 * @param latex строка формулы, например: "\\frac{a^2 + b^2}{c}"
 * @param fontSizeDp "кегль" в dp (визуально соответствует sp ~ dp в данном случае)
 * @param maxWidth ограничение по ширине контейнера; если 0.dp — подстраивается под родителя
 */
@Composable
fun Latex(
    latex: String,
    modifier: Modifier = Modifier,
    fontSizeDp: Dp = 20.dp,
    maxWidth: Dp = 0.dp
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val containerMaxWidthPx = with(density) {
            (if (maxWidth > 0.dp) maxWidth else this@BoxWithConstraints.maxWidth).toPx()
        }

        // Генерим Bitmap при изменении входных данных
        val bitmap by remember(latex, fontSizeDp, containerMaxWidthPx) {
            mutableStateOf(renderLatexToBitmap(latex, fontSizeDp, containerMaxWidthPx))
        }

        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "latex",
                contentScale = ContentScale.Fit
            )
        }
    }
}

private fun renderLatexToBitmap(
    latex: String,
    fontSizeDp: Dp,
    maxWidthPx: Float
): Bitmap? = try {
    val formula = TeXFormula(latex)
    val icon: TeXIcon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, fontSizeDp.value)

    // Масштабируем под доступную ширину (если задано ограничение)
    val scale = if (maxWidthPx > 0f && icon.iconWidth > maxWidthPx) {
        maxWidthPx / icon.iconWidth
    } else 1f

    val outWidth = (icon.iconWidth * scale).toInt().coerceAtLeast(1)
    val outHeight = (icon.iconHeight * scale).toInt().coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Прозрачный фон
    val paint = Paint().apply { isAntiAlias = true }
    canvas.drawARGB(0, 0, 0, 0)

    // Применяем масштаб, рисуем в (0,0)
    canvas.save()
    canvas.scale(scale, scale)
    icon.paintIcon(null, canvas, 0, 0, paint)
    canvas.restore()

    bitmap
} catch (t: Throwable) {
    // На случай синтаксической ошибки в формуле — не валим UI
    null
}