package com.eduappml.ui.common

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext

@Composable
fun LatexView(
    latex: String,
    textColor: Color,
    fontSize: Float = 16f
) {
    val context = LocalContext.current
    // Экранируем специальные символы для HTML
    val escaped = latex
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#039;")
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = WebViewClient()
            }
        },
        update = { webView ->
            val colorHex = String.format("#%02X%02X%02X",
                (textColor.red * 255).toInt(),
                (textColor.green * 255).toInt(),
                (textColor.blue * 255).toInt()
            )
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
                    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
                    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js"></script>
                    <style>
                        body {
                            background: transparent !important;
                            color: $colorHex;
                            font-size: ${fontSize}px;
                            margin: 0;
                            padding: 8px;
                            font-family: sans-serif;
                            word-wrap: break-word;
                            overflow-x: auto;
                        }
                        .katex-display {
                            margin: 0.8em 0;
                            text-align: center;
                            overflow-x: auto;
                            overflow-y: hidden;
                            max-width: 100%;
                            padding: 4px 0;
                            white-space: pre-wrap;
                            word-break: break-word;
                        }
                        .katex {
                            font-size: 1.1em;
                        }
                        .katex .mathnormal {
                            font-style: italic;
                        }
                        @media (max-width: 480px) {
                            .katex-display > .katex {
                                font-size: 0.85em;
                            }
                        }
                        .explanation {
                            margin: 0.5em 0;
                            font-size: 0.9em;
                            color: $colorHex;
                            opacity: 0.8;
                            line-height: 1.4;
                        }
                        h3 {
                            margin: 0.8em 0 0.3em 0;
                            font-weight: bold;
                            font-size: 1.2em;
                        }
                    </style>
                </head>
                <body>
                    <div id="content">$escaped</div>
                    <script>
                        document.addEventListener("DOMContentLoaded", function() {
                            renderMathInElement(document.body, {
                                delimiters: [
                                    {left: '$$', right: '$$', display: true},
                                    {left: '$', right: '$', display: false},
                                    {left: '\\(', right: '\\)', display: false},
                                    {left: '\\[', right: '\\]', display: true}
                                ],
                                throwOnError: false
                            });
                        });
                    </script>
                </body>
                </html>
            """.trimIndent()
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    )
}