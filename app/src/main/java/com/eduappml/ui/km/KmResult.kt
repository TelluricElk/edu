package com.eduappml.ui.km

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.QuizOption
import com.eduappml.ui.common.QuizQuestion
import com.eduappml.ui.common.QuizSection

private val kmQuiz = listOf(
    QuizQuestion(
        "Чем k-средних принципиально отличается от остальных алгоритмов этого приложения?",
        listOf(
            QuizOption("Это обучение без учителя — у данных нет заранее известных меток класса", true),
            QuizOption("Он не использует расстояния между точками", false),
            QuizOption("Он требует размеченных данных больше, чем остальные алгоритмы", false),
            QuizOption("Он работает только с одним признаком", false)
        ),
        "K-средних сам находит структуру в данных, не опираясь на правильные ответы — в этом смысл кластеризации в противовес классификации."
    ),
    QuizQuestion(
        "Как выбрать разумное число кластеров k?",
        listOf(
            QuizOption("По методу локтя — найти точку, после которой инерция перестаёт заметно падать", true),
            QuizOption("k всегда равно числу признаков в данных", false),
            QuizOption("k нужно всегда брать максимально большим", false),
            QuizOption("Число k алгоритм подбирает сам, без участия человека", false)
        ),
        "Инерция монотонно убывает с ростом k, но после определённого k выигрыш становится незначительным — это и есть точка «локтя»."
    ),
    QuizQuestion(
        "Почему случайная инициализация центроидов может давать разные результаты?",
        listOf(
            QuizOption("Алгоритм Ллойда сходится к локальному, а не обязательно глобальному минимуму инерции", true),
            QuizOption("Это баг конкретной реализации, в правильной реализации такого не бывает", false),
            QuizOption("Результат всегда одинаков независимо от инициализации", false),
            QuizOption("Инициализация влияет только на скорость, но не на итоговый результат", false)
        ),
        "Разные стартовые центроиды могут привести алгоритм в разные локальные минимумы — отсюда практика k-means++ и запуска алгоритма несколько раз."
    ),
    QuizQuestion(
        "Что делает шаг «обновления» в алгоритме Ллойда?",
        listOf(
            QuizOption("Пересчитывает каждый центроид как среднее точек, отнесённых к его кластеру на текущем шаге", true),
            QuizOption("Удаляет кластеры с малым числом точек", false),
            QuizOption("Случайно перемешивает точки между кластерами", false),
            QuizOption("Увеличивает число кластеров на единицу", false)
        ),
        "После того как точки распределены по ближайшим центроидам, каждый центроид «переезжает» в среднюю точку своего нового кластера."
    )
)

@Composable
fun KmResult(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit) {
    val textColor = Color.White
    val accent = Color(0xFFE63946)

    val state = remember { KmLab.run(k = 4, iterations = 15, seed = 7) }

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "K-средних",
        onBack = onBack,
        accent = accent,
        modifier = modifier
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Эталонное решение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("Параметры: k = 4, 15 итераций алгоритма Ллойда.", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Итоговая инерция: ${"%.0f".format(state.inertia)}",
                    color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Полученные знания", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "K-средних минимизирует суммарное расстояние от точек до центроидов своих кластеров.",
                    "Алгоритм Ллойда — это повторение шагов «присвоение → обновление» до сходимости.",
                    "Случайная инициализация может привести к разным, не всегда оптимальным результатам.",
                    "Число кластеров k выбирают по данным — например, методом локтя."
                ).forEach { Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = kmQuiz, textColor = textColor)
    }
}
