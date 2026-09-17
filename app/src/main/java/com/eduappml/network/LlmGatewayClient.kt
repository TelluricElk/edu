package com.eduappml.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Клиент шлюза llm-gateway (третий движок чата, llm.armintel.ru).
 *
 * Почему не переиспользован [ApiClient]: у того базовый адрес Supabase и
 * интерцептор, который в каждый запрос подставляет ANON_KEY. Шлюзу нужен
 * ровно обратный набор — свой порт и токен пользователя в `Authorization`,
 * который передаётся параметром метода. Смешивать это в одном клиенте значило
 * бы трогать работающий путь к GigaChat ради нового движка.
 *
 * Ключ armintel здесь намеренно отсутствует: приложение ходит не в armintel,
 * а в собственный шлюз, который держит токен у себя в `.env`.
 */
object LlmGatewayClient {

    /**
     * Адрес шлюза. Должен заканчиваться на "/".
     *
     * Порт 8788 — тот же, что PORT в `server/llm-gateway/.env`. Схема станет
     * `https://`, как только перед сервером появится nginx с сертификатом
     * (одновременно с `wss://` у VoiceConfig).
     */
    const val BASE_URL = "http://157.22.206.53:8788/"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // Сам шлюз ждёт модель до 75 секунд и только потом отвечает ошибкой,
        // так что читать ответ приложению приходится дольше обычного HTTP.
        // Меньше этого значения — и пользователь увидит «нет сети» там, где
        // сервер на самом деле работает.
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            // Не BODY: с вложением тело — мегабайты base64, logcat на них
            // захлёбывается (та же причина, что в ApiClient).
            level = HttpLoggingInterceptor.Level.HEADERS
        })
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: LlmGatewayApi = retrofit.create(LlmGatewayApi::class.java)
}
