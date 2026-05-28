package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Dynamic theme container for custom Geometric Palette
object GeometricTheme {
    var isDark: Boolean by mutableStateOf(false)
    var isManualOverride: Boolean by mutableStateOf(false)

    val background get() = if (isDark) Color(0xFF0F0E13) else Color(0xFFFDF8F3)
    val textPrimary get() = if (isDark) Color(0xFFFAF7FC) else Color(0xFF1D1B20)
    val textSecondary get() = if (isDark) Color(0xFFB5B2BC) else Color(0xFF49454F)
    val accentPrimary get() = if (isDark) Color(0xFFBBACF1) else Color(0xFF6750A4)
    val accentSecondary get() = if (isDark) Color(0xFF332060) else Color(0xFFEADDFF)
    val containerPrimary get() = if (isDark) Color(0xFF16141B) else Color(0xFFF3EDF7)
    val containerSecondary get() = if (isDark) Color(0xFF201D27) else Color(0xFFF7F2FA)
    val borderLight get() = if (isDark) Color(0xFF2E2A36) else Color(0xFFE7E0EC)
    val borderDark get() = if (isDark) Color(0xFF423E4C) else Color(0xFFCAC4D0)
    val accentPill get() = if (isDark) Color(0xFF432D77) else Color(0xFFE8DEF8)
    val safeGreen get() = if (isDark) Color(0xFF0D3319) else Color(0xFFE6F4EA)
    val safeGreenText get() = if (isDark) Color(0xFF6CE68E) else Color(0xFF137333)
    val warningRed get() = if (isDark) Color(0xFF3B0C08) else Color(0xFFFCE8E6)
    val warningRedText get() = if (isDark) Color(0xFFF6756B) else Color(0xFFC5221F)
    val backgroundShift get() = if (isDark) Color(0xFF110F15) else Color(0xFFFAF2E8)
}

// Geometric Balance Palette
val GeoBackground get() = GeometricTheme.background
val GeoTextPrimary get() = GeometricTheme.textPrimary
val GeoTextSecondary get() = GeometricTheme.textSecondary
val GeoAccentPrimary get() = GeometricTheme.accentPrimary
val GeoAccentSecondary get() = GeometricTheme.accentSecondary
val GeoContainerPrimary get() = GeometricTheme.containerPrimary
val GeoContainerSecondary get() = GeometricTheme.containerSecondary
val GeoBorderLight get() = GeometricTheme.borderLight
val GeoBorderDark get() = GeometricTheme.borderDark
val GeoAccentPill get() = GeometricTheme.accentPill
val GeoSafeGreen get() = GeometricTheme.safeGreen
val GeoSafeGreenText get() = GeometricTheme.safeGreenText
val GeoWarningRed get() = GeometricTheme.warningRed
val GeoWarningRedText get() = GeometricTheme.warningRedText
val GeoBackgroundShift get() = GeometricTheme.backgroundShift
