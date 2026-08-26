package com.eduappml.ui.glossary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.common.Adaptive
import com.eduappml.ui.common.BottomPillButton
import com.eduappml.ui.common.GlossaryRow

data class GlossaryItem(
    val id: String,
    val abbreviation: String,
    val fullName: String,
    val description: String,
    val accent: Color
)

private val glossaryData = listOf(
    GlossaryItem("lr", "LR", "Линейная регрессия", "Находит прямую линию, наилучшим образом описывающую зависимость.", Color(0xFFFF6B6B)),
    GlossaryItem("logr", "LogR", "Логистическая регрессия", "Оценивает вероятность класса через сигмоиду.", Color(0xFFFFD93D)),
    GlossaryItem("knn", "KNN", "Метод k-ближайших соседей", "Классифицирует по голосованию ближайших соседей.", Color(0xFF6BCB77)),
    GlossaryItem("nb", "NB", "Наивный Байес", "Сравнивает распределения признаков по классам через теорему Байеса.", Color(0xFF4D96FF)),
    GlossaryItem("svm", "SVM", "Метод опорных векторов", "Ищет границу с максимальным зазором между классами.", Color(0xFFB5179E)),
    GlossaryItem("dt", "DT", "Дерево решений", "Классифицирует последовательными вопросами о признаках.", Color(0xFFFF914D)),
    GlossaryItem("rf", "RF", "Случайный лес", "Объединяет голоса множества деревьев на случайных подвыборках.", Color(0xFF9D4EDD)),
    GlossaryItem("gb", "GB", "Градиентный бустинг", "Каждое новое дерево исправляет ошибки предыдущих.", Color(0xFF00C2A8)),
    GlossaryItem("km", "KM", "Кластеризация k-средних", "Группирует данные вокруг k центроидов без разметки.", Color(0xFFE63946))
)

@Composable
fun GlossaryScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    // Отступы под системные бары — вместо прежнего «на глаз» vertical = 16.dp.
    // Приложение рисует под барами, поэтому без этого заголовок на телефоне с
    // высоким статус-баром подлезал под часы, а кнопка внизу — под панель
    // навигации. Ширина списка ограничена: на планшете строка глоссария
    // растягивалась на весь экран, и аббревиатура слева оказывалась в другом
    // конце экрана от значка справа — глазу нечем их связать.
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = Adaptive.ContentMaxWidth)
                .fillMaxSize()
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ГЛОССАРИЙ",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Text(
                text = "Полные названия ключевых алгоритмов",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(glossaryData) { item ->
                    GlossaryRow(
                        abbreviation = item.abbreviation,
                        fullName = item.fullName,
                        description = item.description,
                        accent = item.accent,
                        graphId = item.id
                    )
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 4.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            BottomPillButton(
                text = "Назад к карте знаний",
                onClick = onBack,
                modifier = Modifier.wrapContentSize()
            )
        }
    }
}
