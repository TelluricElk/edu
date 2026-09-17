package com.eduappml.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Визуал повторяет «стеклянный» стиль ChatScreen: белые иконки на затемнённом
 * фоне, тот же сиреневый акцент, те же 48dp у круглых кнопок. Цвета продублированы
 * константами, а не взяты из ui.chat, чтобы пакет voice не зависел от пакета
 * чата — зависимость нужна ровно в одну сторону.
 */
private val VoiceAccent = Color(0xFFB9B6FF)
private val VoiceStroke = Color.White.copy(alpha = 0.22f)
private val VoiceDanger = Color(0xFFFF8A8A)

/**
 * Кнопка голосового режима — ставится в строку ввода рядом со скрепкой.
 * Когда режим включён, подсвечивается акцентом и превращается в «стоп».
 */
@Composable
fun VoiceModeButton(
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (active) VoiceAccent.copy(alpha = 0.35f)
                else Color.White.copy(alpha = 0.12f)
            )
            .border(1.dp, VoiceStroke, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (active) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = if (active) "Выключить голосовой режим" else "Голосовой режим",
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Полоса состояния голосовой сессии — показывается над полем ввода, пока режим
 * активен: слушает ассистент или говорит, что именно он произносит сейчас,
 * и кнопки «перебить» / «выйти».
 */
@Composable
fun VoicePanel(
    state: VoiceState,
    liveText: String,
    onInterrupt: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state != VoiceState.IDLE && state != VoiceState.IDLE_SESSION,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, VoiceStroke, RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VoicePulse(state)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (state) {
                        VoiceState.CONNECTING -> "Подключаюсь…"
                        VoiceState.LISTENING -> "Слушаю"
                        VoiceState.SPEAKING -> "Отвечаю"
                        VoiceState.ERROR -> "Голосовой режим прерван"
                        VoiceState.IDLE, VoiceState.IDLE_SESSION -> ""
                    },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val hint = when {
                    liveText.isNotBlank() -> liveText
                    state == VoiceState.LISTENING -> "Говорите — я замолчу, когда вы закончите"
                    else -> ""
                }
                if (hint.isNotBlank()) {
                    Text(
                        text = hint,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 2
                    )
                }
            }

            if (state == VoiceState.SPEAKING) {
                VoiceSmallButton(Icons.Filled.Stop, "Прервать ответ", onInterrupt)
            }
            VoiceSmallButton(Icons.Filled.Close, "Выйти из голосового режима", onStop)
        }
    }
}

@Composable
private fun VoiceSmallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** Пульсирующий кружок: спокойный, когда слушаем, и заметно живее, когда отвечаем. */
@Composable
private fun VoicePulse(state: VoiceState) {
    val transition = rememberInfiniteTransition(label = "voice-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = if (state == VoiceState.SPEAKING) 1.25f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == VoiceState.SPEAKING) 420 else 1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voice-pulse-scale"
    )

    val color = if (state == VoiceState.ERROR) VoiceDanger else VoiceAccent

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .scale(pulse)
                .background(color.copy(alpha = 0.25f), CircleShape)
        )
        Icon(
            imageVector = if (state == VoiceState.SPEAKING) Icons.Filled.GraphicEq else Icons.Filled.Mic,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Переключатель озвучки — ставится в шапку чата, когда выбран «Яндекс».
 * Работает и для голосовых вопросов, и для напечатанных: включено — модель
 * проговаривает ответ, выключено — только пишет.
 */
@Composable
fun VoiceSpeakerToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                if (enabled) VoiceAccent.copy(alpha = 0.35f)
                else Color.White.copy(alpha = 0.10f)
            )
            .border(1.dp, VoiceStroke, CircleShape)
            .clickable { onToggle(!enabled) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (enabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
            contentDescription = if (enabled) "Выключить озвучку" else "Включить озвучку",
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Строка с ошибкой голосового режима — отдельный Snackbar на этом экране заводить не за чем. */
@Composable
fun VoiceErrorLine(message: String?, modifier: Modifier = Modifier) {
    if (message.isNullOrBlank()) return
    Text(
        text = message,
        color = VoiceDanger,
        fontSize = 12.sp,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 2.dp)
    )
}
