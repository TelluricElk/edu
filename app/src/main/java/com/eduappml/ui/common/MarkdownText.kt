package com.eduappml.ui.common

import android.util.Log
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin

@Composable
fun MarkdownText(
    markdown: String,
    textColor: Color,
    textSizeSp: Float = 16f,
    formulaScale: Float = 1.4f
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
                val builder = Markwon.builder(textView.context)
                    .usePlugin(MarkwonInlineParserPlugin.create())
                    .usePlugin(
                        JLatexMathPlugin.create(
                            textView.textSize * formulaScale
                        )
                    )
                val markwon = builder.build()
                val normalized = normalizeMathDelimiters(markdown)
                markwon.setMarkdown(textView, normalized)
            } catch (e: Exception) {
                Log.e("MarkdownText", "Render error: ${e.message}")
                textView.text = markdown
            }
        }
    )
}

private fun normalizeMathDelimiters(src: String): String {
    var s = src
    s = s.replace(Regex("""(?<!\$)\$(\s+)(?!\$)"""), """$""")
    s = s.replace(Regex("""(?<!\$)(\s+)\$(?!\$)"""), """$""")
    s = s.replace(Regex("""\$\$\s+"""), """$$""")
    s = s.replace(Regex("""\s+\$\$"""), """$$""")
    return s
}