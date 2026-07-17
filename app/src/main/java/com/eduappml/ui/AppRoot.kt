package com.eduappml.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.eduappml.ThemeManager
import com.eduappml.managers.SessionManager
import com.eduappml.ui.auth.LoginScreen
import com.eduappml.ui.auth.RegisterScreen
import com.eduappml.ui.auth.VerificationScreen
import com.eduappml.ui.common.UnifiedBackground
import com.eduappml.ui.detail.NodeDetailScreen
import com.eduappml.ui.glossary.GlossaryScreen
import com.eduappml.ui.info.InfoScreen
import com.eduappml.ui.interactive.InteractiveScreen
import com.eduappml.ui.menu.MenuScreen
import com.eduappml.ui.menu.defaultEdges
import com.eduappml.ui.menu.defaultNodes
import com.eduappml.ui.splash.SplashForeground
import com.eduappml.ui.third.ThirdScreen
import com.eduappml.ui.third.thirdEdges
import com.eduappml.ui.third.thirdNodes
import kotlin.math.abs

private sealed class Screen {
    data object Splash : Screen()
    data object Login : Screen()
    data object Register : Screen()
    data object Verification : Screen()
    data object Menu   : Screen()
    data object Third  : Screen()
    data class Info(
        val id: String,
        val returnTo: Screen,
        val initialTab: Int = 0,
        val fromDetail: Boolean = false
    ) : Screen()
    data object Glossary : Screen()
    data class NodeDetail(
        val id: String,
        val label: String,
        val screenType: String,
        val returnTo: Screen
    ) : Screen()
    data class Interactive(
        val id: String,
        val screenType: String,
        val returnTo: Screen
    ) : Screen()
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val isDarkTheme = ThemeManager.isDarkThemeActive(context)

    SessionManager.init(context)
    var isLoggedIn by remember { mutableStateOf(SessionManager.isLoggedIn()) }

    var screen by remember { mutableStateOf(
        if (isLoggedIn) Screen.Splash else Screen.Login
    ) }

    fun navigateToMain() {
        screen = Screen.Splash
    }

    UnifiedBackground(isDarkTheme = isDarkTheme) {
        Box(Modifier.fillMaxSize()) {
            Crossfade(
                targetState = screen,
                modifier = Modifier.fillMaxSize(),
                label = "screens"
            ) { currentScreen ->
                when (currentScreen) {
                    Screen.Splash -> SplashForeground(
                        modifier = Modifier.fillMaxSize(),
                        onFinishedFadeOut = { screen = Screen.Menu }
                    )
                    Screen.Login -> LoginScreen(
                        onLoginSuccess = {
                            isLoggedIn = true
                            navigateToMain()
                        },
                        onNavigateToRegister = { screen = Screen.Register }
                    )
                    Screen.Register -> RegisterScreen(
                        onRegisterSuccess = { /* не используется */ },
                        onNavigateToVerification = { screen = Screen.Verification },
                        onNavigateToLogin = { screen = Screen.Login }
                    )
                    Screen.Verification -> VerificationScreen(
                        onVerificationSuccess = {
                            isLoggedIn = true
                            navigateToMain()
                        },
                        onBack = { screen = Screen.Register }
                    )
                    Screen.Menu -> MenuScreen(
                        modifier = Modifier.fillMaxSize(),
                        onBack = { screen = Screen.Splash },
                        onForward = { screen = Screen.Third },
                        onOpenInfo = { id ->
                            // устаревший
                        },
                        onOpenGlossary = { screen = Screen.Glossary },
                        onOpenDetail = { id ->
                            val label = defaultNodes().find { it.id == id }?.label ?: id
                            screen = Screen.NodeDetail(id, label, "classic", Screen.Menu)
                        }
                    )
                    Screen.Third -> ThirdScreen(
                        modifier = Modifier.fillMaxSize(),
                        onBack = { screen = Screen.Menu },
                        onOpenInfo = { id ->
                            // устаревший
                        },
                        onOpenDetail = { id ->
                            val label = thirdNodes().find { it.id == id }?.label ?: id
                            screen = Screen.NodeDetail(id, label, "neural", Screen.Third)
                        }
                    )
                    is Screen.Info -> {
                        val edges = when (currentScreen.returnTo) {
                            Screen.Menu -> defaultEdges()
                            Screen.Third -> thirdEdges()
                            else -> emptyList()
                        }
                        val screenType = when (currentScreen.returnTo) {
                            Screen.Menu -> "classic"
                            Screen.Third -> "neural"
                            else -> "classic"
                        }
                        InfoScreen(
                            modifier = Modifier.fillMaxSize(),
                            id = currentScreen.id,
                            screenType = screenType,
                            edges = edges,
                            initialTab = currentScreen.initialTab,
                            fromDetail = currentScreen.fromDetail,
                            onBack = { screen = currentScreen.returnTo }
                        )
                    }
                    Screen.Glossary -> GlossaryScreen(
                        modifier = Modifier.fillMaxSize(),
                        onBack = { screen = Screen.Menu }
                    )
                    is Screen.NodeDetail -> {
                        val edges = when (currentScreen.screenType) {
                            "classic" -> defaultEdges()
                            "neural" -> thirdEdges()
                            else -> emptyList()
                        }
                        val auraColor = getAuraColor(currentScreen.id, currentScreen.screenType)
                        NodeDetailScreen(
                            modifier = Modifier.fillMaxSize(),
                            nodeId = currentScreen.id,
                            nodeLabel = currentScreen.label,
                            screenType = currentScreen.screenType,
                            edges = edges,
                            auraColor = auraColor,
                            onBack = { screen = currentScreen.returnTo },
                            onOpenInfo = { id, tab ->
                                screen = Screen.Info(id, currentScreen, tab, fromDetail = true)
                            },
                            onOpenInteractive = { id ->
                                screen = Screen.Interactive(id, currentScreen.screenType, currentScreen.returnTo)
                            }
                        )
                    }
                    is Screen.Interactive -> {
                        val label = when (currentScreen.screenType) {
                            "classic" -> defaultNodes().find { it.id == currentScreen.id }?.label ?: currentScreen.id
                            "neural" -> thirdNodes().find { it.id == currentScreen.id }?.label ?: currentScreen.id
                            else -> currentScreen.id
                        }
                        InteractiveScreen(
                            modifier = Modifier.fillMaxSize(),
                            onBack = { screen = currentScreen.returnTo },
                            id = currentScreen.id,
                            screenType = currentScreen.screenType,
                            title = label
                        )
                    }
                }
            }
        }
    }
}

private fun getAuraColor(id: String, screenType: String): Color {
    val palette = listOf(
        Color(0xFFFF6B6B),
        Color(0xFFFFD93D),
        Color(0xFF6BCB77),
        Color(0xFF4D96FF),
        Color(0xFFB5179E),
        Color(0xFFFF914D),
        Color(0xFF9D4EDD),
        Color(0xFF00C2A8)
    )
    val index = abs(id.hashCode()) % palette.size
    return palette[index]
}