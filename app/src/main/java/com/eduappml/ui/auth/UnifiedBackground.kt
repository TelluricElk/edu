package com.eduappml.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eduappml.ui.AnimatedCurvedGradientBackground

@Composable
fun UnifiedBackground(
    isDarkTheme: Boolean,
    content: @Composable () -> Unit
) {
    if (isDarkTheme) {
        AnimatedCurvedGradientBackground(isDarkTheme = true) {
            content()
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            WaveBackground(modifier = Modifier.fillMaxSize())
            content()
        }
    }
}