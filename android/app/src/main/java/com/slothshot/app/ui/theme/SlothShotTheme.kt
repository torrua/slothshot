package com.slothshot.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Elite Obsidian Glass Dark Palette
val SlothDarkBackground = Color(0xFF090D16)
val SlothSurface = Color(0xFF111726)
val SlothSurfaceVariant = Color(0xFF1B2438)
val SlothPrimary = Color(0xFF10B981)
val SlothPrimaryDark = Color(0xFF059669)
val SlothSecondary = Color(0xFF38BDF8)
val SlothTextPrimary = Color(0xFFF8FAFC)
val SlothTextSecondary = Color(0xFF94A3B8)
val SlothBorder = Color(0x1AFFFFFF)
val SlothWarning = Color(0xFFF59E0B)
val SlothError = Color(0xFFF43F5E)

// Light Mode Palette
val SlothLightBackground = Color(0xFFF8FAFC)
val SlothLightSurface = Color(0xFFFFFFFF)
val SlothLightSurfaceVariant = Color(0xFFE2E8F0)
val SlothLightTextPrimary = Color(0xFF0F172A)
val SlothLightTextSecondary = Color(0xFF64748B)

private val DarkColorScheme = darkColorScheme(
    primary = SlothPrimary,
    onPrimary = Color.Black,
    secondary = SlothSecondary,
    onSecondary = Color.Black,
    background = SlothDarkBackground,
    onBackground = SlothTextPrimary,
    surface = SlothSurface,
    onSurface = SlothTextPrimary,
    surfaceVariant = SlothSurfaceVariant,
    onSurfaceVariant = SlothTextSecondary,
    outline = SlothBorder,
    error = SlothError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = SlothPrimaryDark,
    onPrimary = Color.White,
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    background = SlothLightBackground,
    onBackground = SlothLightTextPrimary,
    surface = SlothLightSurface,
    onSurface = SlothLightTextPrimary,
    surfaceVariant = SlothLightSurfaceVariant,
    onSurfaceVariant = SlothLightTextSecondary,
    error = SlothError,
    onError = Color.White
)

@Composable
fun SlothShotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
