package com.eduappml.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Единая "рамка" для всех шести экранов подтемы (Теория / Задача / Мат. основа /
 * Код / Интерактив / Решение): небольшая цветная плашка-рубрика, крупный
 * заголовок, прокручиваемое содержимое и закреплённые снизу кнопки Назад/Далее.
 *
 * Это самый «дешёвый» файл проекта в смысле адаптивности: он один отвечает за
 * раскладку 6 экранов * 19 тем, поэтому две правки здесь исправляют почти весь
 * текстовый контент приложения сразу.
 *
 * ЧТО ИЗМЕНИЛОСЬ:
 *
 * 1. Ширина колонки текста ограничена [Adaptive.ContentMaxWidth] и колонка
 *    центрирована. Раньше был только `padding(horizontal = 20.dp)`: на телефоне
 *    это нормальная строка, а на планшете в ландшафте строка растягивалась на
 *    900+dp — читать такое почти невозможно, глаз теряет начало следующей строки.
 *
 * 2. Отступы сверху и снизу считаются от системных инсетов, а не подобраны
 *    числом. Приложение рисует под системными барами
 *    (`WindowCompat.setDecorFitsSystemWindows(window, false)` в MainActivity),
 *    поэтому прежние `top = 28.dp` и `bottom = 34.dp` были на самом деле
 *    «на глаз подобранная высота панели навигации конкретного телефона»:
 *    на устройстве с трёхкнопочной навигацией (48dp) кнопка «Назад» частично
 *    уезжала под системную панель, а на планшете без панели висела слишком
 *    высоко. Тот же подход уже применён в ChatScreen.kt — теперь он единый.
 */
@Composable
fun LessonScaffold(
    eyebrow: String,
    title: String,
    onBack: () -> Unit,
    onNext: (() -> Unit)? = null,
    nextLabel: String = "Далее →",
    accent: Color = Color(0xFFB9B6FF),
    scrollable: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler { onBack() }

    val textColor = Color(0xFFF2EEFF)

    Box(modifier = modifier.fillMaxSize()) {

        // Внешний контейнер занимает всю ширину и удерживает системные инсеты;
        // внутренняя колонка ограничена по ширине и центрирована.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            val columnModifier = Modifier
                // ВАЖЕН ПОРЯДОК: widthIn должен стоять ДО fillMaxSize.
                // Модификаторы применяются снаружи внутрь, поэтому сначала
                // ограничиваем максимальную ширину, а уже потом «растягиваемся»
                // в неё. При обратном порядке fillMaxSize задаёт жёсткую ширину
                // во весь экран, и ограничение просто не срабатывает.
                .widthIn(max = Adaptive.ContentMaxWidth)
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                // Снизу — место под закреплённый ряд кнопок (48dp кнопка +
                // воздух). Системная панель навигации сюда уже не входит:
                // её держит navigationBarsPadding выше.
                .padding(top = 12.dp, bottom = 84.dp)
                .let { if (scrollable) it.verticalScroll(rememberScrollState()) else it }

            Column(modifier = columnModifier) {
                EyebrowPill(text = eyebrow, accent = accent)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = title,
                    color = textColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 18.dp)
                )
                content()
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BottomPillButton(text = "Назад", onClick = onBack)
            if (onNext != null) {
                BottomPillButton(text = nextLabel, onClick = onNext)
            }
        }
    }
}

@Composable
private fun EyebrowPill(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .background(accent.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
    }
}
