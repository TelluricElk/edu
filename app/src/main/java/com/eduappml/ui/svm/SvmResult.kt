package com.eduappml.ui.svm

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
import kotlin.math.roundToInt

private val svmQuiz = listOf(
    QuizQuestion(
        "Что такое опорные векторы?",
        listOf(
            QuizOption("Точки, ближайшие к разделяющей границе, которые и определяют её положение", true),
            QuizOption("Все точки обучающей выборки без исключения", false),
            QuizOption("Точки, которые SVM классифицировал неверно", false),
            QuizOption("Центры классов", false)
        ),
        "Только точки с ненулевым alpha (лежащие на границе зазора или внутри него) входят в итоговую формулу предсказания."
    ),
    QuizQuestion(
        "Как параметр C влияет на модель?",
        listOf(
            QuizOption("Большое C делает границу жёсткой и чувствительной к каждой точке, малое — мягкой и более обобщающей", true),
            QuizOption("C влияет только на скорость обучения, не на саму границу", false),
            QuizOption("Чем больше C, тем меньше опорных векторов при любых данных", false),
            QuizOption("C определяет число классов", false)
        ),
        "C — это компромисс между шириной зазора и числом допустимых нарушений: большое C сильнее наказывает за ошибки, сужая зазор."
    ),
    QuizQuestion(
        "Зачем нужен kernel trick?",
        listOf(
            QuizOption("Чтобы строить нелинейные границы, не вычисляя явно отображение в пространство высокой размерности", true),
            QuizOption("Чтобы ускорить работу с линейно разделимыми данными", false),
            QuizOption("Чтобы уменьшить число опорных векторов до нуля", false),
            QuizOption("Это чисто техническая оптимизация, не влияющая на форму границы", false)
        ),
        "Функция ядра вычисляет скалярное произведение в новом пространстве напрямую по исходным координатам — без явного перехода в это пространство."
    ),
    QuizQuestion(
        "Когда стоит выбрать RBF-ядро вместо линейного?",
        listOf(
            QuizOption("Когда классы нельзя разделить прямой линией — нужна изогнутая граница", true),
            QuizOption("Всегда — RBF гарантированно лучше линейного", false),
            QuizOption("Только если данных очень много", false),
            QuizOption("RBF и линейное ядро дают одинаковый результат", false)
        ),
        "Линейное ядро строит только прямую границу. RBF-ядро позволяет границе изгибаться вокруг локальных скоплений точек."
    )
)

@Composable
fun SvmResult(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit) {
    val textColor = Color.White
    val accent = Color(0xFFB5179E)

    val model = remember { SvmLab.train(c = 1f, kernel = SvmKernel.LINEAR, gamma = 0.3f, iterations = 800) }
    val accuracy = remember { SvmLab.accuracy(model, SvmLab.testSet) }
    val svCount = remember { SvmLab.supportVectorCount(model) }

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "SVM",
        onBack = onBack,
        accent = accent,
        modifier = modifier
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Эталонное решение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("Параметры: C = 1, ядро — линейное.", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Точность на контрольной выборке: ${(accuracy * 100).roundToInt()}%, опорных векторов: $svCount из ${SvmLab.trainSet.size}",
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
                    "SVM максимизирует зазор между классами, а не просто разделяет их.",
                    "Только опорные векторы определяют положение границы — остальные точки не влияют.",
                    "Параметр C управляет компромиссом между шириной зазора и числом ошибок.",
                    "Kernel trick позволяет строить нелинейные границы без явного перехода в пространство высокой размерности."
                ).forEach { Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = svmQuiz, textColor = textColor)
    }
}
