package com.eduappml.managers

import android.content.Context
import android.content.SharedPreferences
import com.eduappml.data.models.UserProgress

object SessionManager {
    private const val PREFS_NAME = "app_session"
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_CLASSIC_UNLOCKED = "classic_unlocked"
    private const val KEY_NEURAL_UNLOCKED = "neural_unlocked"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun saveUserId(userId: String) {
        prefs.edit().putString(KEY_USER_ID, userId).apply()
    }

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun saveUsername(username: String) {
        prefs.edit().putString(KEY_USERNAME, username).apply()
    }

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun saveProgress(progress: UserProgress) {
        prefs.edit()
            .putString(KEY_CLASSIC_UNLOCKED, progress.unlockedClassic.joinToString(","))
            .putString(KEY_NEURAL_UNLOCKED, progress.unlockedNeural.joinToString(","))
            .apply()
    }

    fun loadProgress(): UserProgress? {
        val classicStr = prefs.getString(KEY_CLASSIC_UNLOCKED, null) ?: return null
        val neuralStr = prefs.getString(KEY_NEURAL_UNLOCKED, null) ?: return null
        return UserProgress(
            unlockedClassic = if (classicStr.isNotEmpty()) classicStr.split(",") else emptyList(),
            unlockedNeural = if (neuralStr.isNotEmpty()) neuralStr.split(",") else emptyList()
        )
    }

    fun isLoggedIn(): Boolean = getToken() != null && getUserId() != null
}