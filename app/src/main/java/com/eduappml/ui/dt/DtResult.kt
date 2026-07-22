package com.eduappml.ui.dt

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

private val dtQuiz = listOf(
    QuizQuestion(
        "Почему глубокое дерево склонно к переобучению?",
        listOf(
            QuizOption("Оно начинает подстраиваться под шум и случайные особенности обучающей выборки, а не под общую закономерность", true),
            QuizOption("Глубокое дерево всегда работает медленнее", false),
            QuizOption("Глубина дерева не влияет на переобучение", false),
            QuizOption("Глубокое дерево нельзя визуализировать", false)
        ),
        "С ростом глубины каждый лист описывает всё меньше объектов — вплоть до отдельных точек обучающей выборки, включая шумные."
    ),
    QuizQuestion(
        "Чем отличается индекс Джини от энтропии как меры неоднородности?",
        listOf(
            QuizOption("Они по-разному штрафуют смешение классов, но на практике часто выбирают похожие разбиения", true),
            QuizOption("Джини применим только к числовым признакам, энтропия — только к категориальным", false),
            QuizOption("Энтропия всегда даёт более глубокие деревья", false),
            QuizOption("Это два названия одной и той же формулы", false)
        ),
        "Обе меры равны нулю при чистом узле и максимальны при равном смешении классов, но энтропия использует логарифм и чуть иначе взвешивает промежуточные случаи."
    ),
    QuizQuestion(
        "Зачем нужен параметр min_samples_split?",
        listOf(
            QuizOption("Чтобы не делать разбиения в узлах с слишком малым числом объектов — это тоже защита от переобучения", true),
            QuizOption("Чтобы ускорить обучение на больших данных", false),
            QuizOption("Чтобы гарантировать баланс классов", false),
            QuizOption("Он не связан с построением дерева", false)
        ),
        "Разбиение по 2–3 объектам почти никогда не отражает реальную закономерность — это почти всегда шум."
    ),
    QuizQuestion(
        "Чем CART отличается от ID3?",
        listOf(
            QuizOption("CART использует индекс Джини и строит только бинарные разбиения, ID3 — энтропию и работает с категориальными признаками", true),
            QuizOption("CART не умеет работать с числовыми признаками", false),
            QuizOption("ID3 — более новый и точный алгоритм, чем CART", false),
            QuizOption("Разницы между ними нет", false)
        ),
        "CART (используется и в этом приложении, и в scikit-learn) всегда делит узел ровно на два, что упрощает и ускоряет построение дерева."
    )
)

@Composable
fun DtResult(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit) {
    val textColor = Color.White
    val accent = Color(0xFFFF914D)

    val tree = remember { DtLab.buildTree(DtLab.trainSet, DtCriterion.GINI, maxDepth = 4, minSamplesSplit = 4) }
    val trainAcc = remember { DtLab.accuracy(tree, DtLab.trainSet) }
    val testAcc = remember { DtLab.accuracy(tree, DtLab.testSet) }

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "Дерево решений",
        onBack = onBack,
        accent = accent,
        modifier = modifier
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Эталонное решение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("Параметры: max_depth = 4, критерий — Джини.", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Точность: обучающая ${(trainAcc * 100).roundToInt()}%, контрольная ${(testAcc * 100).roundToInt()}%",
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
                    "Дерево строится жадно: на каждом шаге выбирается разбиение с максимальным приростом информации.",
                    "Индекс Джини и энтропия — две похожие, но не идентичные меры неоднородности узла.",
                    "Глубина дерева — главный рычаг компромисса между точностью на обучении и обобщением.",
                    "Прозрачность решения — главное преимущество деревьев перед менее интерпретируемыми моделями."
                ).forEach { Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = dtQuiz, textColor = textColor)
    }
}
