package com.eduappml.network

import com.eduappml.data.models.ChatRequest
import com.eduappml.data.models.ChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ChatApi {
    // Отправка сообщения пользователя на сервер, который перенаправляет его в GigaChat
    @POST("api/chat")
    suspend fun sendMessage(@Body request: ChatRequest): Response<ChatResponse>
}