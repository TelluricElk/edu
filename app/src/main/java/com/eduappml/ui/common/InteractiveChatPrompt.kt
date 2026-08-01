package com.eduappml.ui.common

/**
 * Готовый черновик вопроса для Edu.AI на экране "Интерактив" — параллель
 * buildResultChatPrompt (ResultChatPrompt.kt), но для случая "пользователь
 * подвигал ползунки и хочет понять, почему получился именно такой результат
 * при ЭТИХ конкретных значениях", а не про фиксированное эталонное решение.
 */
fun buildInteractiveChatPrompt(topicTitle: String, parametersDescription: String, resultDescription: String): String {
    return buildString {
        append("Объясни, пожалуйста, простыми словами, почему при таких параметрах в теме «$topicTitle» (Интерактив) получается именно такой результат.")
        append("\n\nТекущие параметры: $parametersDescription")
        append("\nТекущий результат: $resultDescription")
        append("\n\nПочему именно такое сочетание параметров даёт такой результат?")
    }
}
