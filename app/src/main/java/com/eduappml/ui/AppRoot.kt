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
import com.eduappml.ui.chat.ChatScreen
import com.eduappml.ui.code.CodeScreen
import com.eduappml.ui.common.UnifiedBackground
import com.eduappml.ui.detail.NodeDetailScreen
import com.eduappml.ui.glossary.GlossaryScreen
import com.eduappml.ui.interactive.InteractiveScreen
import com.eduappml.ui.math.MathScreen
import com.eduappml.ui.menu.MenuScreen
import com.eduappml.ui.menu.defaultEdges
import com.eduappml.ui.menu.defaultNodes
import com.eduappml.ui.result.ResultScreen
import com.eduappml.ui.splash.SplashForeground
import com.eduappml.ui.task.TaskScreen
import com.eduappml.ui.theory.TheoryScreen
import com.eduappml.ui.third.ThirdScreen
import com.eduappml.ui.third.thirdEdges
import com.eduappml.ui.third.thirdNodes
import kotlin.math.abs

/**
 * Шесть спутниковых экранов темы (Theory/Task/Math/Code/Interactive/Result)
 * всегда возвращаются на [NodeDetail] соответствующего узла — а не на общую
 * карту алгоритмов — поэтому каждый хранит returnTo = сам объект NodeDetail.
 */
private sealed class Screen {
    data object Splash : Screen()
    data object Login : Screen()
    data object Register : Screen()
    data object Verification : Screen()
    data object Menu   : Screen()
    data object Third  : Screen()
    data object Glossary : Screen()
    data object Chat : Screen()

    data class NodeDetail(
        val id: String,
        val label: String,
        val screenType: String,
        val returnTo: Screen
    ) : Screen()

    data class Theory(val id: String, val screenType: String, val returnTo: Screen) : Screen()
    data class Task(val id: String, val screenType: String, val returnTo: Screen) : Screen()
    data class Math(val id: String, val screenType: String, val returnTo: Screen) : Screen()
    data class Code(val id: String, val screenType: String, val returnTo: Screen) : Screen()
    data class Interactive(val id: String, val screenType: String, val returnTo: Screen) : Screen()
    data class Result(val id: String, val screenType: String, val returnTo: Screen) : Screen()
}

private fun labelFor(id: String, screenType: String): String = when (screenType) {
    "classic" -> defaultNodes().find { it.id == id }?.label ?: id
    "neural" -> thirdNodes().find { it.id == id }?.label ?: id
    else -> id
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
                        onFinishedFadeOut = { screen = Screen.Menu },
                        onOpenChat = { screen = Screen.Chat }
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
                    Screen.Glossary -> GlossaryScreen(
                        modifier = Modifier.fillMaxSize(),
                        onBack = { screen = Screen.Menu }
                    )
                    Screen.Chat -> ChatScreen(
                        modifier = Modifier.fillMaxSize(),
                        onBack = { screen = Screen.Splash }
                    )

                    is Screen.NodeDetail -> {
                        val edges = when (currentScreen.screenType) {
                            "classic" -> defaultEdges()
                            "neural" -> thirdEdges()
                            else -> emptyList()
                        }
                        val auraColor = getAuraColor(currentScreen.id, currentScreen.screenType)
                        // returnTo для всех спутниковых экранов — currentScreen (сам NodeDetail),
                        // а не currentScreen.returnTo — иначе "Назад" уводил бы сразу на общую карту.
                        NodeDetailScreen(
                            modifier = Modifier.fillMaxSize(),
                            nodeId = currentScreen.id,
                            nodeLabel = currentScreen.label,
                            screenType = currentScreen.screenType,
                            edges = edges,
                            auraColor = auraColor,
                            onBack = { screen = currentScreen.returnTo },
                            onOpenTheory = { id -> screen = Screen.Theory(id, currentScreen.screenType, currentScreen) },
                            onOpenTask = { id -> screen = Screen.Task(id, currentScreen.screenType, currentScreen) },
                            onOpenMath = { id -> screen = Screen.Math(id, currentScreen.screenType, currentScreen) },
                            onOpenCode = { id -> screen = Screen.Code(id, currentScreen.screenType, currentScreen) },
                            onOpenInteractive = { id -> screen = Screen.Interactive(id, currentScreen.screenType, currentScreen) },
                            onOpenResult = { id -> screen = Screen.Result(id, currentScreen.screenType, currentScreen) }
                        )
                    }

                    is Screen.Theory -> {
                        TheoryScreen(
                            modifier = Modifier.fillMaxSize(),
                            id = currentScreen.id,
                            title = labelFor(currentScreen.id, currentScreen.screenType),
                            onBack = { screen = currentScreen.returnTo },
                            onNext = { screen = Screen.Task(currentScreen.id, currentScreen.screenType, currentScreen.returnTo) }
                        )
                    }
                    is Screen.Task -> {
                        TaskScreen(
                            modifier = Modifier.fillMaxSize(),
                            id = currentScreen.id,
                            title = labelFor(currentScreen.id, currentScreen.screenType),
                            onBack = { screen = currentScreen.returnTo },
                            onNext = { screen = Screen.Math(currentScreen.id, currentScreen.screenType, currentScreen.returnTo) }
                        )
                    }
                    is Screen.Math -> {
                        MathScreen(
                            modifier = Modifier.fillMaxSize(),
                            id = currentScreen.id,
                            title = labelFor(currentScreen.id, currentScreen.screenType),
                            onBack = { screen = currentScreen.returnTo },
                            onNext = { screen = Screen.Code(currentScreen.id, currentScreen.screenType, currentScreen.returnTo) }
                        )
                    }
                    is Screen.Code -> {
                        CodeScreen(
                            modifier = Modifier.fillMaxSize(),
                            id = currentScreen.id,
                            title = labelFor(currentScreen.id, currentScreen.screenType),
                            onBack = { screen = currentScreen.returnTo },
                            onNext = { screen = Screen.Interactive(currentScreen.id, currentScreen.screenType, currentScreen.returnTo) }
                        )
                    }
                    is Screen.Interactive -> {
                        InteractiveScreen(
                            modifier = Modifier.fillMaxSize(),
                            onBack = { screen = currentScreen.returnTo },
                            id = currentScreen.id,
                            screenType = currentScreen.screenType,
                            title = labelFor(currentScreen.id, currentScreen.screenType),
                            onNext = { screen = Screen.Result(currentScreen.id, currentScreen.screenType, currentScreen.returnTo) }
                        )
                    }
                    is Screen.Result -> {
                        ResultScreen(
                            modifier = Modifier.fillMaxSize(),
                            id = currentScreen.id,
                            title = labelFor(currentScreen.id, currentScreen.screenType),
                            onBack = { screen = currentScreen.returnTo }
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