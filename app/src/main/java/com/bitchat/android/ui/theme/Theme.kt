package com.bitchat.android.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

val BluePrimary = Color(0xFF3B82F6)
val BluePrimaryVariant = Color(0xFF2563EB)
val BlueAccent = Color(0xFF60A5FA)
val BlueBackground = Color(0xFF0B0F14)
val BlueSurface = Color(0xFF131A22)
val BlueElevatedSurface = Color(0xFF1A2330)
val BlueDivider = Color(0xFF273244)
val BlueTextPrimary = Color(0xFFF3F4F6)
val BlueTextSecondary = Color(0xFF94A3B8)
val BlueError = Color(0xFFEF4444)
val BlueOutgoing = Color(0xFF1E3A5F)
val BlueIncoming = Color(0xFF1F2937)

private val DarkColorScheme = darkColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    secondary = BlueAccent,
    onSecondary = Color(0xFF0B0F14),
    tertiary = BluePrimaryVariant,
    background = BlueBackground,
    onBackground = BlueTextPrimary,
    surface = BlueSurface,
    onSurface = BlueTextPrimary,
    surfaceVariant = BlueElevatedSurface,
    onSurfaceVariant = BlueTextSecondary,
    outline = BlueDivider,
    error = BlueError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    secondary = BlueAccent,
    onSecondary = Color(0xFF0B0F14),
    tertiary = BluePrimaryVariant,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = BlueError,
    onError = Color.White
)

@Composable
fun OutpostTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val themePref by ThemePreferenceManager.themeFlow.collectAsState(initial = ThemePreference.System)
    val shouldUseDark = when (darkTheme) {
        true -> true
        false -> false
        null -> when (themePref) {
            ThemePreference.Dark -> true
            ThemePreference.Light -> false
            ThemePreference.System -> isSystemInDarkTheme()
        }
    }

    val colorScheme = if (shouldUseDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    if (!shouldUseDark) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (!shouldUseDark) {
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else 0
            }
            window.navigationBarColor = colorScheme.background.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
