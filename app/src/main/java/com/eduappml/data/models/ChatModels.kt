package com.eduappml.data.models

// Роли участников диалога с GigaChat
object ChatRole {
    const val USER = "user"
    const val ASSISTANT = "assistant"
}

// Одно сообщение истории диалога (отправляется на наш сервер для контекста)
data class ChatHistoryItem(
    val role: String,
    val content: String
)

// Запрос на отправку сообщения (приложение -> наш сервер -> GigaChat)
data class ChatRequest(
    val message: String,
    val history: List<ChatHistoryItem> = emptyList()
)

// Ответ от нашего сервера
data class ChatResponse(
    val success: Boolean = false,
    val reply: String? = null,
    val error: String? = null
)