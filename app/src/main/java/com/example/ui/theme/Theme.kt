package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF6750A4),
    secondary = Color(0xFFE8DEF8),
    background = Color(0xFFFDF8F3),
    surface = Color(0xFFF3EDF7),
    onPrimary = Color.White,
    onSecondary = Color(0xFF1D1B20),
    onBackground = Color(0xFF1D1B20),
    onSurface = Color(0xFF1D1B20),
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFBBACF1),
    secondary = Color(0xFF432D77),
    background = Color(0xFF0F0E13),
    surface = Color(0xFF16141B),
    onPrimary = Color(0xFF1D1B20),
    onSecondary = Color(0xFFFAF7FC),
    onBackground = Color(0xFFFAF7FC),
    onSurface = Color(0xFFFAF7FC),
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  if (!GeometricTheme.isManualOverride) {
    LaunchedEffect(darkTheme) {
      GeometricTheme.isDark = darkTheme
    }
  }

  val activeDark = GeometricTheme.isDark

  val colorScheme = if (activeDark) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography
  ) {
    Box(modifier = Modifier.background(colorScheme.background)) {
      content()
    }
  }
}
