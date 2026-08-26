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

data class NeuralItem(
    val id: String,
    val abbreviation: String,
    val fullName: String,
    val description: String,
    val accent: Color
)

private val neuralData = listOf(
    NeuralItem("fc", "FC", "Полносвязная нейронная сеть", "Каждый нейрон соединён с каждым — база для большинства архитектур.", Color(0xFFFF6B6B)),
    NeuralItem("cnn", "CNN", "Свёрточная нейронная сеть", "Ищет локальные признаки на изображении одним и тем же фильтром.", Color(0xFFFFD93D)),
    NeuralItem("rnn", "RNN", "Рекуррентная нейронная сеть", "Хранит скрытое состояние — память о предыдущих шагах последовательности.", Color(0xFF6BCB77)),
    NeuralItem("tr", "TR", "Трансформер", "Сравнивает все элементы последовательности друг с другом одновременно.", Color(0xFF4D96FF)),
    NeuralItem("gnn", "GNN", "Графовая нейронная сеть", "Распространяет информацию по связям произвольного графа.", Color(0xFFB5179E)),
    NeuralItem("ae", "AE", "Автокодировщик", "Сжимает данные в узкое место и восстанавливает обратно.", Color(0xFFFF914D)),
    NeuralItem("dm", "DM", "Диффузионная модель", "Учится убирать шум, шаг за шагом возвращаясь от хаоса к данным.", Color(0xFF9D4EDD)),
    NeuralItem("gan", "GAN", "Генеративно-состязательная сеть", "Генератор и дискриминатор обучаются в противостоянии друг с другом.", Color(0xFF00C2A8)),
    NeuralItem("som", "SOM", "Самоорганизующаяся карта Кохонена", "Соседние нейроны сетки обучаются представлять похожие данные.", Color(0xFFE63946)),
    NeuralItem("rl", "RL", "Обучение с подкреплением", "Агент учится действовать методом проб и ошибок ради награды.", Color(0xFF00B4D8))
)

@Composable
fun NeuralGlossaryScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    BackHandler { onBack() }

    // См. комментарий-близнец в GlossaryScreen.kt: системные инсеты вместо
    // подобранного числа и ограничение ширины списка для планшета.
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
                text = "Полные названия ключевых архитектур",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(neuralData) { item ->
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
