package com.eduappml.network

import com.eduappml.data.models.ArmintelChatRequest
import com.eduappml.data.models.ArmintelModelsResponse
import com.eduappml.data.models.ChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Шлюз к llm.armintel.ru (`server/llm-gateway/`).
 *
 * Это отдельный сервис на своём порту, а не Edge Function, поэтому и клиент
 * у него свой ([LlmGatewayClient]) — общие заголовки `apikey`/`Authorization`
 * из [ApiClient] шлюзу не нужны и только мешали бы: он ждёт в `Authorization`
 * токен пользователя, а не ANON_KEY.
 */
interface LlmGatewayApi {

    /** Список моделей для выпадашки. Живёт на сервере, в APK не зашит. */
    @GET("models")
    suspend fun models(
        @Header("Authorization") token: String
    ): Response<ArmintelModelsResponse>

    /** Вопрос -> ответ. Формат ответа тот же, что у Edge Function GigaChat. */
    @POST("chat")
    suspend fun chat(
        @Header("Authorization") token: String,
        @Body request: ArmintelChatRequest
    ): Response<ChatResponse>
}
