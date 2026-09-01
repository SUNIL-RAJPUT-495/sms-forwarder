package com.smsforwarder.oppo.ui.theme

import android.app.Activity
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

// ─────────────────────────────────────────────
// OPPO App Color Palette — Deep Teal / Security theme
// Communicates trust and security appropriate for bank SMS.
// ─────────────────────────────────────────────

private val AppTeal900   = Color(0xFF004D40)
private val AppTeal700   = Color(0xFF00796B)
private val AppTeal500   = Color(0xFF009688)
private val AppTeal200   = Color(0xFF80CBC4)
private val AppAmber500  = Color(0xFFFFC107)
private val AppRed400    = Color(0xFFEF5350)
private val AppSurface   = Color(0xFF0F1923)
private val AppBackground= Color(0xFF0A1017)
private val AppOnSurface = Color(0xFFE0F2F1)

private val DarkColorScheme = darkColorScheme(
    primary          = AppTeal500,
    onPrimary        = Color.Black,
    primaryContainer = AppTeal900,
    onPrimaryContainer = AppTeal200,
    secondary        = AppAmber500,
    onSecondary      = Color.Black,
    background       = AppBackground,
    onBackground     = AppOnSurface,
    surface          = AppSurface,
    onSurface        = AppOnSurface,
    surfaceVariant   = Color(0xFF1A2633),
    onSurfaceVariant = Color(0xFFB2DFDB),
    error            = AppRed400,
    onError          = Color.Black,
    outline          = AppTeal700
)

private val LightColorScheme = lightColorScheme(
    primary          = AppTeal700,
    onPrimary        = Color.White,
    primaryContainer = AppTeal200,
    onPrimaryContainer = AppTeal900,
    secondary        = AppAmber500,
    onSecondary      = Color.Black,
    background       = Color(0xFFF0F7F6),
    onBackground     = Color(0xFF004D40),
    surface          = Color.White,
    onSurface        = Color(0xFF004D40),
    surfaceVariant   = Color(0xFFE0F2F1),
    onSurfaceVariant = Color(0xFF00695C),
    error            = AppRed400,
    onError          = Color.White,
    outline          = AppTeal500
)

@Composable
fun SmsForwarderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
