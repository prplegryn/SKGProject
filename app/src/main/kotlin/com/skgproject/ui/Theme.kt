package com.skgproject.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF171615)
val Paper = Color(0xFFF6F3EE)
val PaperAlt = Color(0xFFEDE8DF)
val Mist = Color(0xFFEAF7F0)
val Teal = Color(0xFF2E8C72)
val Coral = Color(0xFFF26D5B)
val Carbon = Color(0xFF090909)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = Coral,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperAlt,
    onSurfaceVariant = Color(0xFF5D5953),
    outline = Color(0x33211F1C),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF77D4B7),
    onPrimary = Color(0xFF08251D),
    secondary = Color(0xFFFFB3A8),
    onSecondary = Color(0xFF481108),
    background = Carbon,
    onBackground = Color(0xFFF2EFE8),
    surface = Color(0xFF12110F),
    onSurface = Color(0xFFF2EFE8),
    surfaceVariant = Color(0xFF24211D),
    onSurfaceVariant = Color(0xFFD4CEC3),
    outline = Color(0x55FFFFFF),
)

@Composable
fun SKGProjectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}

