package com.eduappml.ui.interactive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.ae.AeInteractiveMilitary
import com.eduappml.ui.cnn.CnnInteractiveMilitary
import com.eduappml.ui.common.AskChatButton
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.buildInteractiveChatPrompt
import com.eduappml.ui.dm.DmInteractiveMilitary
import com.eduappml.ui.dt.DtInteractive
import com.eduappml.ui.fc.FcInteractiveMilitary
import com.eduappml.ui.gan.GanInteractiveMilitary
import com.eduappml.ui.gb.GbInteractive
import com.eduappml.ui.gnn.GnnInteractiveMilitary
import com.eduappml.ui.km.KmInteractive
import com.eduappml.ui.knn.KnnInteractive
import com.eduappml.ui.logr.LogrInteractive
import com.eduappml.ui.lr.LrInteractive
import com.eduappml.ui.nb.NbInteractive
import com.eduappml.ui.rf.RfInteractive
import com.eduappml.ui.rl.RlInteractiveMilitary
import com.eduappml.ui.rnn.RnnInteractiveMilitary
import com.eduappml.ui.som.SomInteractiveMilitary
import com.eduappml.ui.svm.SvmInteractive
import com.eduappml.ui.tr.TrInteractiveMilitary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.eduappml.ui.common.designPx

/**
 * Экран "Интерактив" (пузырь-лампочка). Параметры обучения условные —
 * реального обучения модели на устройстве не происходит, всё считается
 * "по требованию" на маленьком фиксированном датасете (см. *Lab.kt каждой темы).
 *
 * [onOpenChat] — колбэк для кнопки "объяснить" рядом с текущим результатом:
 * пользователь подвигал ползунки, получил результат и может спросить у
 * Edu.AI, почему именно так — см. аналогичный параметр в ResultScreen.kt.
 *
 * ПЕРЕХОД НА ЕДИНУЮ ВЕТКУ (ИБ-тематика, сентябрь 2026). Проект уходит от
 * пары «гражданский + военный контент» к одному набору тем про защиту
 * информации. Конвертированные темы диспетчеризуются на файлы БЕЗ суффикса
 * (LogrInteractive), ещё не конвертированные — пока на *Military-варианты.
 * По мере конвертации каждая строка ниже переводится на версию без суффикса,
 * а соответствующий *Military.kt удаляется.
 *
 * Переведены все девять тем первой вкладки: "logr", "lr", "nb", "knn", "svm", "dt", "rf", "gb", "km".
 */
@Composable
fun InteractiveScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    id: String,
    screenType: String,
    title: String? = null,
    onNext: () -> Unit = {},
    onOpenChat: (String) -> Unit = {}
) {
    when (id) {
        "knn" -> KnnInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "lr" -> LrInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "logr" -> LogrInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "svm" -> SvmInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "dt" -> DtInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "nb" -> NbInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "rf" -> RfInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "gb" -> GbInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "km" -> KmInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "fc" -> FcInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "som" -> SomInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "rl" -> RlInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "ae" -> AeInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "gan" -> GanInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "cnn" -> CnnInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "rnn" -> RnnInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "gnn" -> GnnInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "tr" -> TrInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "dm" -> DmInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        else -> ComingSoonInteractive(modifier = modifier, title = title, id = id, onBack = onBack, onNext = onNext)
    }
}

@Composable
private fun ComingSoonInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    id: String,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: id,
        onBack = onBack,
        onNext = onNext,
        accent = Color(0xFF00C2A8),
        modifier = modifier
    ) {
        Text(
            text = "Интерактив для этой темы ещё готовится",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Загляните позже — здесь появится симуляция обучения с настраиваемыми параметрами.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
    }
}

// Старая приватная реализация интерактива k-NN, лежавшая ниже в этом файле,
// удалена при переводе темы knn на ИБ-тематику: публичный
// com.eduappml.ui.knn.KnnInteractive конфликтовал бы с приватной функцией
// того же имени.
