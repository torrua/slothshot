package com.slothshot.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SlothDarkBackground = Color(0xFF0F172A)
val SlothSurface = Color(0xFF1E293B)
val SlothSurfaceVariant = Color(0xFF334155)
val SlothPrimary = Color(0xFF10B981)
val SlothPrimaryDark = Color(0xFF059669)
val SlothSecondary = Color(0xFF38BDF8)
val SlothTextPrimary = Color(0xFFF8FAFC)
val SlothTextSecondary = Color(0xFF94A3B8)
val SlothWarning = Color(0xFFF59E0B)
val SlothError = Color(0xFFEF4444)

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
    error = SlothError,
    onError = Color.White
)

@Composable
fun SlothShotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
