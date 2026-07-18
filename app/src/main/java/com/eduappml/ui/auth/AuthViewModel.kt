package com.eduappml.ui.auth

import android.content.Context
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduappml.data.models.*
import com.eduappml.game.GameManager
import com.eduappml.managers.SessionManager
import com.eduappml.managers.SyncManager
import com.eduappml.network.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class AuthViewModel(private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    private val TAG = "AuthViewModel"

    private var pendingRegistration: PendingRegistration? = null
    private var pendingEmail: String? = null

    // ---------- Регистрация ----------
    fun register(email: String, password: String, username: String, fullName: String? = null) {
        if (email.isEmpty() || password.isEmpty() || username.isEmpty()) {
            _uiState.value = AuthUiState.Error("Заполните все поля")
            return
        }
        if (password.length < 6) {
            _uiState.value = AuthUiState.Error("Пароль должен быть не менее 6 символов")
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val data = mapOf(
                    "username" to username.trim(),
                    "full_name" to (fullName ?: "")
                )
                val request = SupabaseSignUpRequest(email, password, data)
                val response = ApiClient.authApi.register(request)

                if (response.isSuccessful) {
                    val body = response.body()
                    // При включённом подтверждении email access_token не приходит, и это нормально
                    if (body != null && body.user != null) {
                        pendingRegistration = PendingRegistration(
                            email = email,
                            password = password,
                            username = username.trim(),
                            fullName = fullName,
                            userId = body.user.id
                        )
                        pendingEmail = email

                        sendVerificationCode(email)
                        _uiState.value = AuthUiState.NeedsVerification
                    } else {
                        _uiState.value = AuthUiState.Error("Ошибка регистрации: пользователь не создан")
                    }
                } else {
                    val error = response.errorBody()?.string() ?: "Ошибка регистрации"
                    Log.e(TAG, "Registration error: $error")
                    _uiState.value = AuthUiState.Error(error)
                }
            } catch (e: IOException) {
                _uiState.value = AuthUiState.Error("Ошибка сети: ${e.message}")
            } catch (e: HttpException) {
                _uiState.value = AuthUiState.Error("Ошибка сервера: ${e.message}")
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Неизвестная ошибка: ${e.message}")
            }
        }
    }

    // ---------- Отправка кода на почту ----------
    private suspend fun sendVerificationCode(email: String) {
        try {
            Log.d(TAG, "Sending verification code to $email")
            val response = ApiClient.authApi.sendVerificationCode(SendCodeRequest(email))
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    Log.d(TAG, "Code sent successfully")
                } else {
                    Log.e(TAG, "sendVerificationCode failed: ${body?.message}")
                }
            } else {
                Log.e(TAG, "sendVerificationCode HTTP error: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendVerificationCode exception: ${e.message}")
        }
    }

    // ---------- Проверка кода ----------
    fun verifyCode(code: String) {
        val pending = pendingRegistration
        if (pending == null) {
            _uiState.value = AuthUiState.Error("Нет активной регистрации")
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val response = ApiClient.authApi.verifyCode(
                    VerifyCodeRequest(pending.email, code)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    login(pending.email, pending.password)
                    pendingRegistration = null
                    pendingEmail = null
                } else {
                    val msg = response.body()?.message ?: "Неверный код"
                    _uiState.value = AuthUiState.Error(msg)
                }
            } catch (e: IOException) {
                _uiState.value = AuthUiState.Error("Ошибка сети: ${e.message}")
            } catch (e: HttpException) {
                _uiState.value = AuthUiState.Error("Ошибка сервера: ${e.message}")
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Неизвестная ошибка: ${e.message}")
            }
        }
    }

    // ---------- Повторная отправка кода ----------
    fun resendCode() {
        val email = pendingEmail
        if (email == null) {
            _uiState.value = AuthUiState.Error("Нет активной регистрации")
            return
        }
        viewModelScope.launch {
            sendVerificationCode(email)
            _uiState.value = AuthUiState.NeedsVerification
        }
    }

    // ---------- Вход по email ИЛИ логину (используется на экране входа) ----------
    fun loginWithIdentifier(identifier: String, password: String) {
        val trimmed = identifier.trim()
        if (trimmed.isEmpty() || password.isEmpty()) {
            _uiState.value = AuthUiState.Error("Заполните все поля")
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val emailToUse = if (isEmail(trimmed)) {
                    trimmed
                } else {
                    resolveEmailByUsername(trimmed)
                }

                if (emailToUse == null) {
                    _uiState.value = AuthUiState.Error("Неверный логин или пароль")
                    return@launch
                }

                login(emailToUse, password)
            } catch (e: IOException) {
                _uiState.value = AuthUiState.Error("Ошибка сети: ${e.message}")
            } catch (e: HttpException) {
                _uiState.value = AuthUiState.Error("Ошибка сервера: ${e.message}")
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Неизвестная ошибка: ${e.message}")
            }
        }
    }

    private fun isEmail(value: String): Boolean =
        Patterns.EMAIL_ADDRESS.matcher(value).matches()

    private suspend fun resolveEmailByUsername(username: String): String? {
        return try {
            val response = ApiClient.authApi.getEmailByUsername(
                mapOf("username_input" to username)
            )
            if (response.isSuccessful) {
                response.body()?.takeIf { it.isNotBlank() }
            } else {
                Log.e(TAG, "resolveEmailByUsername error: ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "resolveEmailByUsername exception: ${e.message}")
            null
        }
    }

    // ---------- Вход по email (используется после верификации кода и после определения email по логину) ----------
    fun login(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            _uiState.value = AuthUiState.Error("Заполните все поля")
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val request = LoginRequest(email, password)
                val response = ApiClient.authApi.login(request)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.access_token != null && body.user != null) {
                        val pending = pendingRegistration
                        if (pending != null) {
                            val update = ProfileUpdate(
                                username = pending.username,
                                full_name = pending.fullName,
                                email = pending.email
                            )
                            val updateResponse = ApiClient.authApi.updateProfile(
                                token = "Bearer ${body.access_token}",
                                userId = "eq.${body.user.id}",
                                profile = update
                            )
                            if (!updateResponse.isSuccessful) {
                                Log.w(TAG, "Profile update failed: ${updateResponse.errorBody()?.string()}")
                            }
                        }
                        handleAuthSuccess(body.access_token, body.user.id, pending?.username)
                    } else {
                        _uiState.value = AuthUiState.Error("Ошибка входа: email не подтверждён")
                    }
                } else {
                    val error = response.errorBody()?.string() ?: "Неверный логин или пароль"
                    _uiState.value = AuthUiState.Error(error)
                }
            } catch (e: IOException) {
                _uiState.value = AuthUiState.Error("Ошибка сети: ${e.message}")
            } catch (e: HttpException) {
                _uiState.value = AuthUiState.Error("Ошибка сервера: ${e.message}")
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Неизвестная ошибка: ${e.message}")
            }
        }
    }

    // ---------- Выход ----------
    fun logout() {
        SessionManager.clearSession()
        _uiState.value = AuthUiState.Idle
    }

    // ---------- Сброс состояния регистрации ----------
    fun resetPendingRegistration() {
        pendingRegistration = null
        pendingEmail = null
        _uiState.value = AuthUiState.Idle
    }

    // ---------- Обработка успешной аутентификации ----------
    private fun handleAuthSuccess(accessToken: String, userId: String, username: String? = null) {
        SessionManager.saveToken(accessToken)
        SessionManager.saveUserId(userId)
        if (username != null) {
            SessionManager.saveUsername(username)
        }

        viewModelScope.launch {
            try {
                val serverProgress = SyncManager.loadProgressFromServer(context)
                if (serverProgress != null) {
                    SessionManager.saveProgress(serverProgress)
                    GameManager.updateFromProgress(serverProgress)
                } else {
                    GameManager.initDefaultUnlocked(context)
                    val classic = GameManager.getUnlockedNodes("classic")
                    val neural = GameManager.getUnlockedNodes("neural")
                    SyncManager.syncProgress(context, classic, neural)
                    SessionManager.saveProgress(
                        UserProgress(
                            unlockedClassic = classic.toList(),
                            unlockedNeural = neural.toList()
                        )
                    )
                }
                (context as? androidx.activity.ComponentActivity)?.recreate()
                _uiState.value = AuthUiState.Success
            } catch (e: Exception) {
                Log.e(TAG, "Error during auth success handling: ${e.message}")
                _uiState.value = AuthUiState.Success
            }
        }
    }

    // ---------- Состояния ----------
    sealed class AuthUiState {
        object Idle : AuthUiState()
        object Loading : AuthUiState()
        object Success : AuthUiState()
        object NeedsVerification : AuthUiState()
        data class Error(val message: String) : AuthUiState()
    }

    private data class PendingRegistration(
        val email: String,
        val password: String,
        val username: String,
        val fullName: String?,
        val userId: String
    )
}