package com.eduappml.managers

import android.content.Context
import android.util.Log
import com.eduappml.data.models.ProgressData
import com.eduappml.data.models.ProgressUpdate
import com.eduappml.data.models.UserProgress
import com.eduappml.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

object SyncManager {
    private const val TAG = "SyncManager"

    fun syncProgress(context: Context, classic: Set<String>, neural: Set<String>) {
        Log.d(TAG, "syncProgress called with classic=$classic, neural=$neural")

        val token = SessionManager.getToken()
        val userId = SessionManager.getUserId()
        if (token.isNullOrEmpty() || userId.isNullOrEmpty()) {
            Log.w(TAG, "syncProgress: token or userId is null. token=$token, userId=$userId")
            return
        }

        val progressData = ProgressData(
            unlockedClassic = classic.toList(),
            unlockedNeural = neural.toList()
        )
        val jsonData = progressData.toJson()
        Log.d(TAG, "syncProgress: sending data: $jsonData, userId: $userId")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val update = ProgressUpdate(
                    user_id = userId,
                    data = jsonData
                )
                val response = ApiClient.authApi.upsertProgress(
                    token = "Bearer $token",
                    progress = update   // <-- без отдельного фильтра
                )
                if (response.isSuccessful) {
                    Log.d(TAG, "syncProgress: success")
                } else {
                    val error = response.errorBody()?.string() ?: "unknown error"
                    Log.e(TAG, "syncProgress error: $error")
                    Log.e(TAG, "syncProgress error code: ${response.code()}")
                }
            } catch (e: IOException) {
                Log.e(TAG, "syncProgress network error: ${e.message}")
            } catch (e: HttpException) {
                Log.e(TAG, "syncProgress http error: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "syncProgress other error: ${e.message}")
            }
        }
    }

    suspend fun loadProgressFromServer(context: Context): UserProgress? {
        val token = SessionManager.getToken()
        val userId = SessionManager.getUserId()
        if (token.isNullOrEmpty() || userId.isNullOrEmpty()) {
            Log.w(TAG, "loadProgress: token or userId is null")
            return null
        }

        return try {
            val response = ApiClient.authApi.getProgress(
                token = "Bearer $token",
                userId = "eq.$userId"
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.isNotEmpty()) {
                    val record = body[0]
                    val jsonData = record.data
                    Log.d(TAG, "loadProgress: raw data = $jsonData")
                    val progressData = parseProgressData(jsonData)
                    UserProgress(
                        unlockedClassic = progressData.unlockedClassic,
                        unlockedNeural = progressData.unlockedNeural
                    )
                } else {
                    Log.d(TAG, "loadProgress: no progress found (empty body)")
                    null
                }
            } else {
                val error = response.errorBody()?.string() ?: "unknown error"
                Log.e(TAG, "loadProgress error: $error")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadProgress exception: ${e.message}")
            null
        }
    }

    private fun parseProgressData(json: String): ProgressData {
        val classicRegex = "\"unlockedClassic\":\\[(.*?)\\]".toRegex()
        val neuralRegex = "\"unlockedNeural\":\\[(.*?)\\]".toRegex()
        val classicMatch = classicRegex.find(json)
        val neuralMatch = neuralRegex.find(json)

        fun parseList(str: String?): List<String> {
            if (str.isNullOrEmpty()) return emptyList()
            return str.split(",").map { it.trim().replace("\"", "") }.filter { it.isNotEmpty() }
        }

        val classicList = classicMatch?.groupValues?.get(1)?.let { parseList(it) } ?: emptyList()
        val neuralList = neuralMatch?.groupValues?.get(1)?.let { parseList(it) } ?: emptyList()
        return ProgressData(classicList, neuralList)
    }
}