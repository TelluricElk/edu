package com.eduappml

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.eduappml.game.GameManager
import com.eduappml.ui.AppRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Инициализация GameManager
        GameManager.init(this)
        GameManager.initDefaultUnlocked(this)

        // Применяем тему (цвета статус-бара, иконки) до установки контента
        applyThemeByMode()
        super.onCreate(savedInstanceState)

        // Рисуем контент под системными барами
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Прозрачный фон окна (фон будет рисовать Compose)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Отключаем контрастные подложки там, где поддерживается
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        // Полностью «съедаем» инсет-ы
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, _ ->
            WindowInsetsCompat.CONSUMED
        }

        setContent { AppRoot() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Если выбран режим "следовать системе", обновляем оформление при смене темы телефона
        if (ThemeManager.getThemeMode(this) == 0) {
            applyThemeByMode()
        }
    }

    private fun applyThemeByMode() {
        val isDark = ThemeManager.isDarkThemeActive(this)

        // Настройка статус-бара и навигационной панели
        if (isDark) {
            // Тёмная тема: прозрачные бары, иконки белые
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        } else {
            // Светлая тема: прозрачные бары, иконки тёмные
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        // Настройка цвета иконок в системных барах
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (isDark) {
            controller.isAppearanceLightStatusBars = false   // белые иконки
            controller.isAppearanceLightNavigationBars = false
        } else {
            controller.isAppearanceLightStatusBars = true    // тёмные иконки
            controller.isAppearanceLightNavigationBars = true
        }
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // Метод для простого переключения (светлая ↔ тёмная)
    fun toggleTheme() {
        val currentMode = ThemeManager.getThemeMode(this)
        val newMode = when (currentMode) {
            0 -> 2   // система -> тёмная
            1 -> 2   // светлая -> тёмная
            else -> 1 // тёмная -> светлая
        }
        ThemeManager.setThemeMode(this, newMode)
        recreate()
    }

    // Метод для трёхрежимного переключателя (0 – система, 1 – светлая, 2 – тёмная)
    fun setThemeMode(mode: Int) {
        ThemeManager.setThemeMode(this, mode)
        recreate()
    }
}