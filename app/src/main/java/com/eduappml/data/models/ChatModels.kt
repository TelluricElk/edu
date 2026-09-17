package com.eduappml.data.models

import com.google.gson.annotations.SerializedName

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

/**
 * Вложение, которое приложение отправляет на сервер.
 *
 * Бинарные данные едут base64-строкой внутри того же JSON, что и текст
 * сообщения — отдельного multipart-запроса нет специально: Edge Function
 * на Deno разбирает JSON одной строкой, а Retrofit не требует новых
 * зависимостей. Цена решения — раздувание на 4/3 при кодировании, поэтому
 * картинки перед отправкой сжимаются на клиенте (см. ChatAttachments.kt),
 * а размеры ограничены ChatFileLimits.
 */
data class ChatAttachmentPayload(
    @SerializedName("name") val name: String,
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("data") val dataBase64: String
)

/**
 * Запрос на отправку сообщения (приложение -> наш сервер -> GigaChat).
 *
 * ВАЖНО про совместимость: [attachments] и [allowImages] имеют значения по
 * умолчанию, поэтому старый вызов `ChatRequest(message, history)` продолжает
 * компилироваться, а старая версия Edge Function просто проигнорирует
 * незнакомые поля JSON.
 */
data class ChatRequest(
    val message: String,
    val history: List<ChatHistoryItem> = emptyList(),
    val attachments: List<ChatAttachmentPayload> = emptyList(),
    @SerializedName("allow_images") val allowImages: Boolean = true
)

/**
 * Картинка, вернувшаяся из GigaChat (генерация через встроенную функцию
 * text2image). Сервер скачивает её по file_id и отдаёт нам base64 —
 * приложению не нужен ни токен GigaChat, ни доступ к их файловому API.
 */
data class ChatImagePayload(
    @SerializedName("id") val id: String? = null,
    @SerializedName("mime_type") val mimeType: String? = null,
    @SerializedName("data") val dataBase64: String? = null
)

// Ответ от нашего сервера
data class ChatResponse(
    val success: Boolean = false,
    val reply: String? = null,
    val images: List<ChatImagePayload>? = null,
    val error: String? = null
)
