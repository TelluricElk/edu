package com.eduappml.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eduappml.ThemeManager
import com.eduappml.ui.common.MarkdownText
import com.eduappml.ui.common.WaveBackground
import kotlinx.coroutines.launch

private val AccentColor = Color(0xFFB9B6FF)

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    prefillMessage: String? = null
) {
    val context = LocalContext.current
    val isDark = ThemeManager.isDarkThemeActive(context)
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModelFactory(context))

    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Предзаполняем поле ввода, но НЕ отправляем автоматически — пользователь
    // должен увидеть готовый черновик вопроса и сам решить, отправлять его
    // как есть или поправить. См. HANDOFF_BRIEFING.md / обсуждение в чате.
    var input by remember { mutableStateOf(prefillMessage.orEmpty()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Общая функция отправки — используется и круглой кнопкой приложения,
    // и кнопкой "Send" самой клавиатуры (см. keyboardActions ниже). Раньше
    // клавиатура показывала свою кнопку отправки (из-за imeAction = Send),
    // но нажатие на неё ничего не делало — теперь оба пути ведут в одно место.
    fun trySend() {
        if (input.isNotBlank() && !isLoading) {
            val text = input
            input = ""
            scope.launch { viewModel.sendMessage(text) }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!isDark) {
            WaveBackground(modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        )

        // imePadding() — на ВЕСЬ столбец разговора (а не только на строку
        // ввода, как было раньше). Так список сообщений (weight = 1f) сам
        // сжимается, когда появляется клавиатура, и поле ввода оказывается
        // сразу над клавиатурой без зазора — как в Telegram, а не поднимается
        // высоко над ней с пустым местом.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            // Верхняя панель
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Edu.AI",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "ИИ-помощник (бета)",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }

            // Список сообщений
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(message)
                }
                if (isLoading) {
                    item(key = "typing") {
                        TypingBubble()
                    }
                }
            }

            // Поле ввода — navigationBarsPadding() тут отвечает только за
            // случай закрытой клавиатуры (безопасный отступ от системной
            // навигации), клавиатуру целиком обрабатывает imePadding() на
            // Column выше.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp)),
                    placeholder = { Text("Спросите что-нибудь...", color = Color.White.copy(alpha = 0.6f)) },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.White.copy(alpha = 0.12f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.10f),
                        focusedBorderColor = AccentColor.copy(alpha = 0.6f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        cursorColor = AccentColor
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { trySend() }),
                    maxLines = 4
                )

                val canSend = input.isNotBlank() && !isLoading
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (canSend) AccentColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.15f))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .clickable(enabled = canSend) { trySend() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Отправить",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatUiMessage) {
    val bubbleColor = when {
        message.isUser -> AccentColor.copy(alpha = 0.35f)
        message.isError -> Color(0xFFFF6B6B).copy(alpha = 0.25f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    val borderColor = if (message.isUser) AccentColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.25f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(bubbleColor)
                .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // MarkdownText вместо обычного Text — тот же рендерер, что и в
            // "Мат. основе" темы, поэтому формулы из ответа GigaChat
            // отображаются корректно. normalizeChatMath() приводит разные
            // обозначения LaTeX из ответа к единому блочному формату
            // (см. ChatMathFormat.kt).
            MarkdownText(
                markdown = normalizeChatMath(message.text),
                textColor = Color.White,
                textSizeSp = 15f
            )
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(text = "Edu.AI печатает…", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
        }
    }
}
