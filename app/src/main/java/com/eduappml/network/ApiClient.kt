package com.eduappml.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Базовый URL – должен заканчиваться на "/"
    private const val BASE_URL = "http://157.22.206.53/"

    // ANON_KEY из .env файла (тот же, что используется в консоли)
    const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyAgCiAgICAicm9sZSI6ICJhbm9uIiwKICAgICJpc3MiOiAic3VwYWJhc2UtZGVtbyIsCiAgICAiaWF0IjogMTY0MTc2OTIwMCwKICAgICJleHAiOiAxNzk5NTM1NjAwCn0.dc_X5iR_VP_qT0zsiyj_I_OZ2T9FtRU2BBNWN8Bu4GE"

    // Клиент с интерцепторами
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // Отправка вложения — это загрузка нескольких мегабайт base64 плюс
        // время, которое GigaChat тратит на разбор PDF или генерацию картинки.
        // В 30 секунд это не укладывается: запрос обрывался по таймауту уже
        // после того, как сервер принял файл, и пользователь видел "нет сети"
        // при рабочем интернете.
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            // BODY здесь больше нельзя: тело запроса с вложением — это
            // мегабайты base64, и logcat на них захлёбывается (строки режутся,
            // приложение заметно тормозит на каждой отправке файла).
            // HEADERS оставляет всё, что реально нужно для отладки: метод,
            // адрес, код ответа, размеры.
            level = HttpLoggingInterceptor.Level.HEADERS
        })
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("apikey", ANON_KEY)
                .header("Authorization", "Bearer $ANON_KEY")
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
}
