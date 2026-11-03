package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LariKitaDarkColorScheme = darkColorScheme(
    primary = LimeGreenPrimary,
    onPrimary = Color(0xFF09140D),
    primaryContainer = LimeGreenContainer,
    onPrimaryContainer = OnLimeGreenContainer,
    secondary = Color(0xFF86EFAC),
    onSecondary = Color(0xFF062010),
    secondaryContainer = Color(0xFF1B3B2B),
    onSecondaryContainer = Color(0xFFBBF7D0),
    tertiary = PaceMetricColor,
    onTertiary = Color(0xFF001F29),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    error = StatusDanger,
    onError = Color.White
)

private val LariKitaLightColorScheme = lightColorScheme(
    primary = LimeGreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF14532D),
    secondary = Color(0xFF15803D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F5E9),
    onSecondaryContainer = Color(0xFF1E3A2B),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = StatusDanger,
    onError = Color.White
)

@Composable
fun LariKitaTheme(
    darkTheme: Boolean = false, // Default to clean light theme as requested
    dynamicColor: Boolean = false, // Keep custom brand lime/navy colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> LariKitaDarkColorScheme
        else -> LariKitaLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
