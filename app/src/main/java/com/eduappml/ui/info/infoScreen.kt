package com.eduappml.ui.info

import android.content.res.AssetManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.eduappml.ThemeManager
import com.eduappml.data.InfoRepository
import com.eduappml.game.GameManager
import com.eduappml.ui.common.BottomPillButton
import com.eduappml.ui.common.LatexView
import com.eduappml.ui.common.MarkdownText
import com.eduappml.ui.common.WaveBackground
import com.eduappml.ui.menu.EdgeSpec

@Composable
fun InfoScreen(
    modifier: Modifier = Modifier,
    id: String,
    screenType: String,
    edges: List<EdgeSpec>,
    initialTab: Int = 0,
    fromDetail: Boolean = false,
    title: String? = null,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val isDark = ThemeManager.isDarkThemeActive(context)
    val textColor = Color(0xFFF2EEFF)
    val assets: AssetManager = LocalContext.current.assets

    var general by remember(id) { mutableStateOf<String?>(null) }
    var math by remember(id) { mutableStateOf<String?>(null) }
    var impl by remember(id) { mutableStateOf<String?>(null) }
    var loadError by remember(id) { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        runCatching {
            InfoRepository.loadAll(assets, id, preferredLang = "ru")
        }.onSuccess { content ->
            general = content.general
            math = content.math
            impl = content.impl
            loadError = null
        }.onFailure { e ->
            loadError = e.message ?: "Ошибка загрузки контента"
        }
    }

    val tabs = listOf(
        TabData("Общая информация", Icons.Filled.MenuBook),
        TabData("Математические основы", Icons.Filled.Functions),
        TabData("Программная реализация", Icons.Filled.Code)
    )

    val pagerState = rememberPagerState(initialPage = initialTab, pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    val isGod = GameManager.isGodMode()
    val unlockedNodes = GameManager.getUnlockedNodes(screenType)
    val currentId = id

    Box(modifier = modifier.fillMaxSize()) {
        if (!isDark) {
            WaveBackground(modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            if (!isGod) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    val neighbors = edges.filter { it.fromId == currentId || it.toId == currentId }
                        .flatMap { listOf(it.fromId, it.toId) }
                        .filter { it != currentId }
                        .filter { !unlockedNodes.contains(it) }
                    if (neighbors.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val count = GameManager.unlockNeighbors(screenType, currentId, edges, context)
                                if (count > 0) {
                                    (context as? androidx.activity.ComponentActivity)?.recreate()
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LockOpen,
                                contentDescription = "Разблокировать смежные",
                                tint = textColor
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Filled.LockOpen,
                            contentDescription = "Всё разблокировано",
                            tint = textColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    color = textColor,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
                HorizontalDivider(
                    color = textColor.copy(alpha = 0.35f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Показываем вкладки только если не из детального режима
            if (!fromDetail) {
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.Transparent,
                    contentColor = textColor,
                    divider = {},
                    indicator = { positions ->
                        SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(positions[pagerState.currentPage]),
                            color = textColor.copy(alpha = 0.35f)
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, tabData ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            },
                            selectedContentColor = textColor,
                            unselectedContentColor = textColor.copy(alpha = 0.65f),
                            icon = {
                                Icon(
                                    imageVector = tabData.icon,
                                    contentDescription = tabData.label,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val content = when (page) {
                    0 -> general
                    1 -> math
                    else -> impl
                }
                val errorMessage = loadError

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp, bottom = 110.dp)
                ) {
                    if (errorMessage != null) {
                        MarkdownText(
                            markdown = "### Ошибка загрузки\n\n$errorMessage",
                            textColor = textColor,
                            textSizeSp = 16f
                        )
                    } else {
                        if (content.isNullOrBlank()) {
                            NotFoundPlaceholder(id = id, tabIndex = page, textColor = textColor)
                        } else {
                            // Для математики используем LatexView, для остальных MarkdownText
                            if (page == 1) {
                                LatexView(
                                    latex = content,
                                    textColor = textColor,
                                    fontSize = 18f
                                )
                            } else {
                                MarkdownText(
                                    markdown = content,
                                    textColor = textColor,
                                    textSizeSp = 18f
                                )
                            }
                        }
                    }
                }
            }
        }

        BottomPillButton(
            text = "Назад",
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp)
        )
    }
}

private data class TabData(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun NotFoundPlaceholder(
    id: String,
    tabIndex: Int,
    textColor: Color
) {
    val section = when (tabIndex) {
        0 -> "general"
        1 -> "math"
        else -> "impl"
    }
    val hint = """
        ### Контент не найден

        Ожидались файлы в assets:
        - info/classic/$id/$section.ru.md
        - info/classic/$id/$section.en.md
        - info/classic/$id/$section.md
        - info/$id/$section.ru.md
        - info/$id/$section.en.md
        - info/$id/$section.md
        - legacy: info/classic/$id.ru.md | $id.en.md | $id.md
    """.trimIndent()
    MarkdownText(
        markdown = hint,
        textColor = textColor.copy(alpha = 0.95f),
        textSizeSp = 16f
    )
}