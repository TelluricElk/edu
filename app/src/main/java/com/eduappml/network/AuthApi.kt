package com.eduappml.network

import com.eduappml.data.models.*
import retrofit2.Response
import retrofit2.http.*

interface AuthApi {
    // === Аутентификация ===
    @POST("auth/v1/signup")
    suspend fun register(@Body request: SupabaseSignUpRequest): Response<AuthResponse>

    @POST("auth/v1/token?grant_type=password")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    // === Профиль ===
    @GET("rest/v1/profiles")
    suspend fun getProfile(
        @Header("Authorization") token: String,
        @Query("user_id") userId: String
    ): Response<List<ProfileResponse>>

    @GET("rest/v1/profiles")
    suspend fun getProfileByUsername(
        @Header("Authorization") token: String,
        @Query("username") username: String
    ): Response<List<ProfileResponse>>

    @PATCH("rest/v1/profiles")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Query("user_id") userId: String,
        @Body profile: ProfileUpdate
    ): Response<Unit>

    // === RPC для получения email по логину ===
    @POST("rest/v1/rpc/get_email_by_username")
    suspend fun getEmailByUsername(
        @Body request: Map<String, String>
    ): Response<String>

    // === Прогресс ===
    @GET("rest/v1/user_progress")
    suspend fun getProgress(
        @Header("Authorization") token: String,
        @Query("user_id") userId: String
    ): Response<List<ProgressResponse>>

    @POST("rest/v1/user_progress?on_conflict=user_id")
    suspend fun upsertProgress(
        @Header("Authorization") token: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body progress: ProgressUpdate
    ): Response<Unit>

    // === Edge Functions для верификации ===
    @POST("functions/v1/send-verification-code")
    suspend fun sendVerificationCode(
        @Body request: SendCodeRequest
    ): Response<SendCodeResponse>

    @POST("functions/v1/verify-code")
    suspend fun verifyCode(
        @Body request: VerifyCodeRequest
    ): Response<VerifyCodeResponse>
}