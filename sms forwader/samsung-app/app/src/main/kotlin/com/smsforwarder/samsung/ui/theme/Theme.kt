package com.smsforwarder.samsung.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Samsung app palette — Deep Indigo / Trust theme
private val Indigo900   = Color(0xFF1A237E)
private val Indigo700   = Color(0xFF303F9F)
private val Indigo500   = Color(0xFF3F51B5)
private val Indigo200   = Color(0xFF9FA8DA)
private val Cyan400     = Color(0xFF26C6DA)
private val Green400    = Color(0xFF66BB6A)
private val Red400      = Color(0xFFEF5350)
private val SurfaceDark = Color(0xFF0D1117)
private val BgDark      = Color(0xFF080C12)
private val OnSurfDark  = Color(0xFFE8EAF6)

private val DarkColorScheme = darkColorScheme(
    primary          = Indigo500,
    onPrimary        = Color.White,
    primaryContainer = Indigo900,
    onPrimaryContainer = Indigo200,
    secondary        = Cyan400,
    onSecondary      = Color.Black,
    background       = BgDark,
    onBackground     = OnSurfDark,
    surface          = SurfaceDark,
    onSurface        = OnSurfDark,
    surfaceVariant   = Color(0xFF151D2C),
    onSurfaceVariant = Indigo200,
    error            = Red400,
    onError          = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary          = Indigo700,
    onPrimary        = Color.White,
    primaryContainer = Indigo200,
    onPrimaryContainer = Indigo900,
    secondary        = Cyan400,
    onSecondary      = Color.Black,
    background       = Color(0xFFF3F4FF),
    onBackground     = Indigo900,
    surface          = Color.White,
    onSurface        = Indigo900
)

val AppTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp)
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
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
}
