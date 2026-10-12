package com.example.securechat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val ChatboxLightColorScheme = lightColorScheme(
    primary = CoralRed,
    onPrimary = PureWhite,
    primaryContainer = LightGray,
    onPrimaryContainer = DarkText,
    secondary = CoralRed,
    onSecondary = PureWhite,
    background = PureWhite,
    onBackground = DarkText,
    surface = PureWhite,
    onSurface = DarkText,
    surfaceVariant = LightGray,
    onSurfaceVariant = DarkText,
    error = CoralRed,
    onError = PureWhite
)

private val ChatboxDarkColorScheme = darkColorScheme(
    primary = CoralRed,
    onPrimary = PureWhite,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = DarkTextColor,
    secondary = CoralRed,
    onSecondary = PureWhite,
    background = DarkBackground,
    onBackground = DarkTextColor,
    surface = DarkSurface,
    onSurface = DarkTextColor,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextColor,
    error = CoralRed,
    onError = PureWhite
)

@Composable
fun SecureChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ChatboxDarkColorScheme
        else -> ChatboxLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = Shapes,
        typography = Typography,
        content = content
    )
}
