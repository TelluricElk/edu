package com.eduappml.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.eduappml.ui.common.UnifiedBackground

@Composable
fun VerificationScreen(
    onVerificationSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(context))
    val uiState by viewModel.uiState.collectAsState()

    var code by remember { mutableStateOf("") }
    var isResending by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is AuthViewModel.AuthUiState.Success) {
            onVerificationSuccess()
        }
    }

    UnifiedBackground(isDarkTheme = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Подтверждение email",
                fontSize = 24.sp,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "На вашу почту отправлен код подтверждения",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { code = it.take(6) },
                label = { Text("Код из письма", color = Color.White.copy(alpha = 0.7f)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (uiState is AuthViewModel.AuthUiState.Error) {
                Text(
                    text = (uiState as AuthViewModel.AuthUiState.Error).message,
                    color = Color.Red,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Button(
                onClick = {
                    if (code.length == 6) {
                        viewModel.verifyCode(code)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is AuthViewModel.AuthUiState.Loading
            ) {
                if (uiState is AuthViewModel.AuthUiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("Подтвердить")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = {
                    isResending = true
                    viewModel.resendCode()
                    isResending = false
                },
                enabled = !isResending
            ) {
                Text("Отправить код повторно", color = Color.White.copy(alpha = 0.7f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = {
                    viewModel.resetPendingRegistration()
                    onBack()
                }
            ) {
                Text("Назад к регистрации", color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}