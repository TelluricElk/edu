package com.eduappml.ui.chat

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduappml.data.models.ChatHistoryItem
import com.eduappml.data.models.ChatRequest
import com.eduappml.data.models.ChatRole
import com.eduappml.network.ChatApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID

data class ChatUiMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false
)

class ChatViewModel(private val context: Context) : ViewModel() {
    private val TAG = "ChatViewModel"

    private val _messages = MutableStateFlow(
        listOf(
            ChatUiMessage(
                text = "Привет! Я — Edu.AI, помогу разобраться с материалом. Задай вопрос 🙂",
                isUser = false
            )
        )
    )
    val messages: StateFlow<List<ChatUiMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isLoading.value) return

        _messages.value = _messages.value + ChatUiMessage(text = trimmed, isUser = true)
        _isLoading.value = true

        val history = _messages.value
            .filterNot { it.isError }
            .map {
                ChatHistoryItem(
                    role = if (it.isUser) ChatRole.USER else ChatRole.ASSISTANT,
                    content = it.text
                )
            }

        viewModelScope.launch {
            try {
                val response = ChatApiClient.chatApi.sendMessage(
                    ChatRequest(message = trimmed, history = history)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val reply = response.body()?.reply?.takeIf { it.isNotBlank() }
                        ?: "Сервер не вернул ответ."
                    _messages.value = _messages.value + ChatUiMessage(text = reply, isUser = false)
                } else {
                    val err = response.body()?.error
                        ?: response.errorBody()?.string()
                        ?: "неизвестная ошибка сервера"
                    Log.e(TAG, "sendMessage error: $err")
                    _messages.value = _messages.value + ChatUiMessage(
                        text = "Сервер чата вернул ошибку. Попробуйте позже.",
                        isUser = false,
                        isError = true
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error: ${e.message}", e)
                _messages.value = _messages.value + ChatUiMessage(
                    text = "Не удалось подключиться к серверу чата. Проверьте адрес сервера и подключение к интернету.",
                    isUser = false,
                    isError = true
                )
            } catch (e: HttpException) {
                Log.e(TAG, "HTTP error: ${e.message}", e)
                _messages.value = _messages.value + ChatUiMessage(
                    text = "Сервер вернул ошибку. Попробуйте позже.",
                    isUser = false,
                    isError = true
                )
            } catch (t: Throwable) {
                // Ловим ВСЁ, включая Error (например ExceptionInInitializerError,
                // если ChatApiClient не смог инициализироваться из-за некорректного
                // BASE_URL) — чтобы это никогда не роняло приложение целиком,
                // а показывалось как обычное сообщение об ошибке в чате.
                Log.e(TAG, "Unexpected error/throwable: ${t.message}", t)
                _messages.value = _messages.value + ChatUiMessage(
                    text = "Произошла непредвиденная ошибка при обращении к серверу чата. " +
                            "Проверьте настройки сервера в ChatApiClient.kt (BASE_URL).",
                    isUser = false,
                    isError = true
                )
            } finally {
                _isLoading.value = false
            }
        }
    }
}