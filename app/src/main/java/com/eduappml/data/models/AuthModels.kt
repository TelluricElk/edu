package com.eduappml.data.models

// === Аутентификация ===
data class SupabaseSignUpRequest(
    val email: String,
    val password: String,
    val data: Map<String, String>? = null
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val access_token: String? = null,
    val token_type: String? = null,
    val expires_in: Int? = null,
    val refresh_token: String? = null,
    val user: User? = null
)

data class User(
    val id: String,
    val email: String,
    val created_at: String
)

// === Профиль ===
data class ProfileResponse(
    val user_id: String,
    val username: String? = null,
    val full_name: String? = null,
    val email: String? = null
)

data class ProfileUpdate(
    val username: String? = null,
    val full_name: String? = null,
    val email: String? = null
)

// === Прогресс ===
data class ProgressData(
    val unlockedClassic: List<String> = emptyList(),
    val unlockedNeural: List<String> = emptyList()
) {
    fun toJson(): String {
        val classic = unlockedClassic.joinToString(separator = ",", prefix = "[", postfix = "]")
        val neural = unlockedNeural.joinToString(separator = ",", prefix = "[", postfix = "]")
        return """{"unlockedClassic":$classic,"unlockedNeural":$neural}"""
    }
}

data class ProgressUpdate(
    val user_id: String,
    val data: String
)

data class ProgressResponse(
    val id: Long,
    val user_id: String,
    val data: String,
    val updated_at: String
)

data class UserProgress(
    val unlockedClassic: List<String>,
    val unlockedNeural: List<String>
)

// === Верификация email ===
data class SendCodeRequest(
    val email: String
)

data class SendCodeResponse(
    val success: Boolean,
    val message: String? = null,
    val code: String? = null
)

data class VerifyCodeRequest(
    val email: String,
    val code: String
)

data class VerifyCodeResponse(
    val success: Boolean,
    val message: String? = null
)