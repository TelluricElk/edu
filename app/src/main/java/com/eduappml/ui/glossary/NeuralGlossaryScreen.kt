package com.eduappml.ui.glossary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.common.BottomPillButton

data class NeuralItem(
    val abbreviation: String,
    val fullName: String,
    val description: String,
    val auraColor: Color
)

private val neuralData = listOf(
    NeuralItem("FC", "Полносвязная нейронная сеть", "Базовая архитектура, где каждый нейрон соединён с каждым. Лежит в основе большинства моделей.", Color(0xFFFF6B6B)),
    NeuralItem("CNN", "Свёрточная нейронная сеть", "Специализируется на обработке изображений с помощью свёрток, фильтров и карт признаков.", Color(0xFFFFD93D)),
    NeuralItem("RNN", "Рекуррентная нейронная сеть", "Добавляет память о предыдущих состояниях для работы с последовательностями.", Color(0xFF6BCB77)),
    NeuralItem("TR", "Трансформер", "Использует механизм внимания для обработки последовательностей, пришёл на смену RNN.", Color(0xFF4D96FF)),
    NeuralItem("GNN", "Графовая нейронная сеть", "Оперирует данными в виде графов, обрабатывая связи между вершинами.", Color(0xFFB5179E)),
    NeuralItem("AE", "Автокодировщик", "Сжимает данные в латентное пространство для восстановления.", Color(0xFFFF914D)),
    NeuralItem("DM", "Диффузионная модель", "Генерирует данные через постепенное добавление/удаление шума.", Color(0xFF9D4EDD)),
    NeuralItem("GAN", "Генеративно-состязательная сеть", "Генерирует данные через состязание генератора и дискриминатора.", Color(0xFF00C2A8)),
    NeuralItem("SOM", "Самоорганизующаяся карта Кохонена", "Метод снижения размерности и кластеризации данных.", Color(0xFFE63946)),
    NeuralItem("RL", "Обучение с подкреплением", "Агент обучается принимать решения, максимизируя награду.", Color(0xFF00B4D8))
)

@Composable
fun NeuralGlossaryScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    BackHandler { onBack() }

    Box(modifier = modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 16.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(neuralData) { item ->
                    NeuralGlossaryRow(item)
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 16.dp),
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

@Composable
private fun NeuralGlossaryRow(item: NeuralItem) {
    val bubbleSize = 36.dp

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(bubbleSize)
                .drawBehind {
                    val radius = size.minDimension / 2f
                    drawCircle(
                        color = item.auraColor.copy(alpha = 0.25f),
                        radius = radius * 1.3f,
                        center = center
                    )
                    drawCircle(
                        color = item.auraColor.copy(alpha = 0.15f),
                        radius = radius * 1.6f,
                        center = center
                    )
                }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.abbreviation,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        ) {
            Text(
                text = item.fullName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = item.description,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.9f),
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.size(40.dp)) // пустое место вместо графика
    }
}