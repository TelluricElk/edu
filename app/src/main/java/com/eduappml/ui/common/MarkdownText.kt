package com.eduappml.ui.common

import android.util.Log
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin

/**
 * Рендер markdown с формулами.
 *
 * ВАЖНО про формулы: используем только БЛОЧНЫЙ синтаксис LaTeX — "$$" на
 * отдельной строке, формула на следующей строке(ах), закрывающие "$$" на
 * отдельной строке, и обязательно пустые строки до и после (это отдельный
 * абзац). Это самая надёжная, штатная форма поддержки JLatexMathPlugin —
 * не зависит от инлайн-парсера и его настроек. Одиночный "$...$" внутри
 * обычного предложения НЕ используется намеренно (это более хрупкий путь,
 * зависящий от тонкой настройки инлайн-парсера) — переменные внутри текста
 * просто пишутся обычными буквами.
 *
 * Раньше здесь была функция normalizeMathDelimiters(), которая схлопывала
 * пробелы/переносы строк вокруг "$" и "$$". Это ломало ровно то, что нужно
 * блочному синтаксису (перенос строки сразу после открывающих "$$"), поэтому
 * функция убрана целиком — markdown передаётся в Markwon как есть.
 */
@Composable
fun MarkdownText(
    markdown: String,
    textColor: Color,
    textSizeSp: Float = 16f,
    formulaScale: Float = 1.5f
) {
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                setTextColor(
                    android.graphics.Color.argb(
                        (textColor.alpha * 255).toInt(),
                        (textColor.red * 255).toInt(),
                        (textColor.green * 255).toInt(),
                        (textColor.blue * 255).toInt()
                    )
                )
                textSize = textSizeSp
            }
        },
        update = { textView ->
            try {
                val markwon = Markwon.builder(textView.context)
                    .usePlugin(JLatexMathPlugin.create(textView.textSize * formulaScale))
                    .build()
                markwon.setMarkdown(textView, markdown)
            } catch (e: Exception) {
                Log.e("MarkdownText", "Render error: ${e.message}", e)
                textView.text = markdown
            }
        }
    )
}
