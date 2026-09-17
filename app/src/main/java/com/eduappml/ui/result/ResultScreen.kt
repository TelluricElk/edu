package com.eduappml.ui.result

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.ae.AeResultMilitary
import com.eduappml.ui.cnn.CnnResultMilitary
import com.eduappml.ui.common.AskChatButton
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.buildResultChatPrompt
import com.eduappml.ui.dm.DmResultMilitary
import com.eduappml.ui.dt.DtResult
import com.eduappml.ui.fc.FcResultMilitary
import com.eduappml.ui.gan.GanResultMilitary
import com.eduappml.ui.gb.GbResult
import com.eduappml.ui.gnn.GnnResultMilitary
import com.eduappml.ui.km.KmResult
import com.eduappml.ui.knn.KnnResult
import com.eduappml.ui.common.QuizSection
import com.eduappml.ui.logr.LogrResult
import com.eduappml.ui.lr.LrResult
import com.eduappml.ui.nb.NbResult
import com.eduappml.ui.rf.RfResult
import com.eduappml.ui.rl.RlResultMilitary
import com.eduappml.ui.rnn.RnnResultMilitary
import com.eduappml.ui.som.SomResultMilitary
import com.eduappml.ui.svm.SvmResult
import com.eduappml.ui.tr.TrResultMilitary
import kotlin.math.roundToInt

/**
 * Экран "Решение задачи" (пузырь-алмаз). Показывает итог эталонного прогона
 * на рекомендованных гиперпараметрах и встроенный тест по теме — для каждого
 * алгоритма своя реализация, диспетчеризуемая по id.
 *
 * [onOpenChat] — колбэк для кнопки "Почему получился именно такой результат"
 * (см. аналогичный параметр в MathScreen.kt) — прокидывается в каждую тему.
 *
 * ПЕРЕХОД НА ЕДИНУЮ ВЕТКУ (ИБ-тематика, сентябрь 2026). Проект уходит от
 * пары «гражданский + военный контент» к одному набору тем про защиту
 * информации. Конвертированные темы диспетчеризуются на файлы БЕЗ суффикса
 * (LogrResult), ещё не конвертированные — пока на *ResultMilitary-варианты.
 * По мере конвертации каждая строка ниже переводится на версию без суффикса,
 * а соответствующий *Military.kt удаляется.
 *
 * Переведены все девять тем первой вкладки: "logr", "lr", "nb", "knn", "svm", "dt", "rf", "gb", "km".
 */
@Composable
fun ResultScreen(
    modifier: Modifier = Modifier,
    id: String,
    title: String? = null,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    when (id) {
        "knn" -> KnnResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "lr" -> LrResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "logr" -> LogrResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "svm" -> SvmResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "dt" -> DtResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "nb" -> NbResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "rf" -> RfResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "gb" -> GbResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "km" -> KmResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "fc" -> FcResultMilitary(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "som" -> SomResultMilitary(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "rl" -> RlResultMilitary(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "ae" -> AeResultMilitary(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "gan" -> GanResultMilitary(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "cnn" -> CnnResultMilitary(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "rnn" -> RnnResultMilitary(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "gnn" -> GnnResultMilitary(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "tr" -> TrResultMilitary(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "dm" -> DmResultMilitary(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        else -> ComingSoonResult(modifier = modifier, title = title, id = id, onBack = onBack)
    }
}

@Composable
private fun ComingSoonResult(modifier: Modifier = Modifier, title: String?, id: String, onBack: () -> Unit) {
    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: id,
        onBack = onBack,
        accent = Color(0xFFE53935),
        modifier = modifier
    ) {
        Text(
            text = "Решение задачи для этой темы ещё готовится",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// Старая приватная реализация решения k-NN (knnQuiz + KnnResult), лежавшая
// ниже в этом файле, удалена при переводе темы knn на ИБ-тематику: публичный
// com.eduappml.ui.knn.KnnResult конфликтовал бы с приватной функцией того же имени.
