package com.voxumgrau.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ============================================================
// JARVIS THEME — Design System
// ============================================================
// Paleta: Holographic Dark + Cyan Electric Accent
// Inspiração: Iron Man JARVIS + Material You 3 + Glassmorphism
// ============================================================

// --- Paleta de Cores ---
val JarvisBlack = Color(0xFF0A0A0F)
val JarvisSurface = Color(0xFF12121A)
val JarvisSurfaceVariant = Color(0xFF1A1A2E)
val JarvisSurfaceGlass = Color(0x661A1A2E) // 40% opacidade
val JarvisBorder = Color(0x3300E5FF) // 20% cyan

// Accent Principal — Cyan Elétrico
val JarvisCyan = Color(0xFF00E5FF)
val JarvisCyanDim = Color(0xFF00B8D4)
val JarvisCyanGlow = Color(0x6600E5FF) // 40% glow
val JarvisCyanSubtle = Color(0x1A00E5FF) // 10% background

// Accent Secundário — Azul Profundo
val JarvisBlue = Color(0xFF3D5AFE)
val JarvisBlueDim = Color(0xFF304FFE)
val JarvisBlueGlow = Color(0x663D5AFE)

// Cores de Estado
val JarvisGreen = Color(0xFF00E676)
val JarvisAmber = Color(0xFFFFAB00)
val JarvisRed = Color(0xFFFF5252)

// Texto
val JarvisTextPrimary = Color(0xFFFFFFFF)
val JarvisTextSecondary = Color(0xFFB0BEC5)
val JarvisTextMuted = Color(0xFF546E7A)

// --- Tipografia ---
val JarvisFontFamily = FontFamily.SansSerif
val JarvisMonoFamily = FontFamily.Monospace

val JarvisTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.ExtraLight,
        fontSize = 36.sp,
        color = JarvisCyan
    ),
    titleLarge = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 20.sp,
        color = JarvisTextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = JarvisTextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        color = JarvisTextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = JarvisTextSecondary
    ),
    bodySmall = TextStyle(
        fontFamily = JarvisMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        color = JarvisTextMuted
    ),
    labelLarge = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = JarvisCyan
    )
)

// --- Tema ---
private val JarvisColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = JarvisBlack,
    primaryContainer = JarvisCyanSubtle,
    onPrimaryContainer = JarvisCyan,
    secondary = JarvisBlue,
    onSecondary = JarvisBlack,
    secondaryContainer = JarvisBlueGlow,
    onSecondaryContainer = JarvisBlue,
    background = JarvisBlack,
    onBackground = JarvisTextPrimary,
    surface = JarvisSurface,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisSurfaceVariant,
    onSurfaceVariant = JarvisTextSecondary,
    error = JarvisRed,
    onError = JarvisBlack,
    outline = JarvisBorder
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = JarvisTypography,
        content = content
    )
}
