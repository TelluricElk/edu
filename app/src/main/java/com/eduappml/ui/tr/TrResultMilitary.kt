package com.eduappml.ui.tr

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.common.AskChatButton
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.QuizOption
import com.eduappml.ui.common.QuizQuestion
import com.eduappml.ui.common.QuizSection
import com.eduappml.ui.common.buildResultChatPrompt
import kotlin.math.roundToInt

private val trQuizMilitary = listOf(
    QuizQuestion(
        "Что принципиально отличает self-attention от того, как RNN обрабатывает последовательность?",
        listOf(
            QuizOption("Каждое слово сразу сравнивается со всеми остальными, без последовательного порядка обработки", true),
            QuizOption("Self-attention обрабатывает слова строго по одному, как RNN", false),
            QuizOption("Self-attention не использует никаких числовых вычислений", false),
            QuizOption("Разницы между ними нет, это два названия одного механизма", false)
        ),
        "Именно отказ от последовательной обработки — и, как следствие, возможность параллельных вычислений — главное практическое преимущество self-attention."
    ),
    QuizQuestion(
        "Зачем в формуле внимания оценки делят на √d_k?",
        listOf(
            QuizOption("Для численной устойчивости — без этого оценки при большой размерности становятся слишком большими, и softmax «затвердевает»", true),
            QuizOption("Это чисто эстетический выбор, не влияющий на результат", false),
            QuizOption("Чтобы гарантировать, что все веса внимания будут равны", false),
            QuizOption("Деление нужно только при обучении, а не при предсказании", false)
        ),
        "Без масштабирования скалярные произведения при большой размерности векторов растут, и softmax начинает выдавать почти вырожденное распределение (единица для одного слова, ноль для всех остальных)."
    ),
    QuizQuestion(
        "Почему трансформеру обязательно нужно positional encoding?",
        listOf(
            QuizOption("Без явного указания позиции self-attention не различает порядок слов вообще", true),
            QuizOption("Positional encoding нужен только для очень длинных предложений", false),
            QuizOption("Это устаревшая часть архитектуры, современные модели её не используют", false),
            QuizOption("Positional encoding заменяет собой механизм внимания целиком", false)
        ),
        "Сам механизм self-attention симметричен относительно перестановки слов — без positional encoding «дозор обнаружил отряд» и «отряд обнаружил дозор» выглядели бы для модели одинаково."
    ),
    QuizQuestion(
        "Почему эмбеддинги слов в интерактиве этой темы заданы вручную, а не обучены?",
        listOf(
            QuizOption("Обучение трансформера требует огромных объёмов текста и вычислений, недоступных в учебном мобильном интерактиве — зато сама формула внимания считается настоящая", true),
            QuizOption("Потому что self-attention технически невозможно вычислить для настоящих обученных эмбеддингов", false),
            QuizOption("Потому что это на самом деле не имеет значения для понимания механизма", false),
            QuizOption("Потому что эмбеддинги в принципе не участвуют в формуле внимания", false)
        ),
        "Это единственная тема приложения, где мы прямо и честно говорим: перед вами настоящая формула, но не настоящее обучение — рамки мобильного интерактива не позволяют обучить трансформер на реальном тексте."
    )
)

/**
 * Военный вариант экрана "Решение задачи" для темы трансформера: та же
 * логика, что у [TrResult] (который остаётся нетронутым и больше не
 * вызывается для id = "tr"), только с предложением-донесением вместо
 * "Маленький кот поймал мышь".
 */
@Composable
fun TrResultMilitary(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit, onOpenChat: (String) -> Unit = {}) {
    val textColor = Color.White
    val accent = Color(0xFF4D96FF)
    val topicTitle = title ?: "Трансформер"

    val weights = remember { TrLabMilitary.attentionWeights() }
    val subjIndex = 1 // "расчёт"
    val objIndex = 5 // "отряд"

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "Трансформер",
        onBack = onBack,
        accent = accent,
        modifier = modifier
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Эталонное наблюдение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Слова «расчёт» и «отряд» — оба существительные, оба одушевлённые в наших вручную заданных признаках.",
                    color = textColor.copy(alpha = 0.85f), fontSize = 14.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Вес внимания «расчёт» → «отряд»: ${(weights[subjIndex][objIndex] * 100).roundToInt()}% — заметно выше, чем к словам с другими признаками.",
                    color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                AskChatButton(accent = accent, onClick = {
                    onOpenChat(
                        buildResultChatPrompt(
                            topicTitle,
                            "вручную заданные эмбеддинги слов (не обученные), настоящая формула self-attention",
                            "вес внимания «расчёт» → «отряд» ${(weights[subjIndex][objIndex] * 100).roundToInt()}%, заметно выше, чем к словам с другими признаками"
                        )
                    )
                })
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Полученные знания", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Self-attention сравнивает каждое слово со всеми остальными одновременно, без последовательного порядка.",
                    "Слова с более похожими векторами-представлениями получают больший вес внимания друг к другу.",
                    "Positional encoding — точная, не обучаемая формула, единственный источник информации о порядке слов.",
                    "Настоящий трансформер обучает проекции Q/K/V на огромных текстовых корпусах — здесь эта часть намеренно упрощена ради честной демонстрации механизма."
                ).forEach { Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = trQuizMilitary, textColor = textColor)
    }
}
