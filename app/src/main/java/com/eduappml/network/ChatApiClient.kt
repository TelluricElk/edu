package com.eduappml.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ChatApiClient {

    // Адрес вашего Node.js-сервера с GigaChat-прокси
    private const val BASE_URL = "http://157.22.206.53:ПОРТ/"

    // Значение из .env сервера (CHAT_API_KEY), которое проверяет authMiddleware.js
    private const val CHAT_API_KEY = "ВАШ_КЛЮЧ"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
            if (CHAT_API_KEY.isNotEmpty()) {
                builder.header("Authorization", "Bearer $CHAT_API_KEY")
            }
            chain.proceed(builder.build())
        }
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val chatApi: ChatApi = retrofit.create(ChatApi::class.java)
}