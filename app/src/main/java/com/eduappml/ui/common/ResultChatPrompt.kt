package com.eduappml.ui.common

/**
 * Готовый черновик вопроса для Edu.AI по эталонному решению на экране "Результат" —
 * см. аналогичные builder-функции buildMathChatPrompt/buildTheoryChatPrompt/
 * buildCodeChatPrompt в соответствующих экранах. [parametersDescription] и
 * [metricDescription] — короткие строки, которые каждая тема формирует сама
 * из своих конкретных параметров и посчитанной метрики.
 */
fun buildResultChatPrompt(topicTitle: String, parametersDescription: String, metricDescription: String): String {
    return buildString {
        append("Объясни, пожалуйста, простыми словами эталонное решение задачи по теме «$topicTitle» (Решение задачи).")
        append("\n\nПараметры: $parametersDescription")
        append("\nРезультат: $metricDescription")
        append("\n\nПочему именно такие параметры дают такой результат?")
    }
}
