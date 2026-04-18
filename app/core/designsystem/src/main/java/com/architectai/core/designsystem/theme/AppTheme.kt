package com.architectai.core.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.architectai.core.designsystem.color.Accent
import com.architectai.core.designsystem.color.Background
import com.architectai.core.designsystem.color.Header
import com.architectai.core.designsystem.color.OnAccent
import com.architectai.core.designsystem.color.OnBackground
import com.architectai.core.designsystem.color.OnHeader
import com.architectai.core.designsystem.color.OnSurface
import com.architectai.core.designsystem.color.Surface
import com.architectai.core.designsystem.color.SurfaceVariant
import com.architectai.core.designsystem.type.AppTypography

private val LightColorScheme = lightColorScheme(
    primary = Header,
    onPrimary = OnHeader,
    primaryContainer = Accent,
    onPrimaryContainer = OnAccent,
    secondary = Accent,
    onSecondary = OnAccent,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    error = Color(0xFFB00020),
    onError = Color.White,
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme // Always use light theme per spec

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
