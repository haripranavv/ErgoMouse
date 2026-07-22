package com.ergomouse.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private val DarkColorScheme = darkColorScheme(
    background = ErgoBlack,
    surface = TrackpadSurface,
    primary = ErgoGreen,
    error = ErgoRed
)

@Composable
fun ErgomouseTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = ErgoBlack.toArgb()
            window.navigationBarColor = ErgoBlack.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}