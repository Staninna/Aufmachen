package com.staninna.aufmachen.ui.theme

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
import com.staninna.aufmachen.data.ThemeMode
import com.staninna.aufmachen.model.Tier

/** Tier accents. Used only for chips, side stripes and progress segments. */
fun Tier.color(dark: Boolean): Color = when (this) {
    Tier.CRITICAL -> if (dark) Color(0xFFFF8A80) else Color(0xFFD32F2F)
    Tier.IMPORTANT -> if (dark) Color(0xFFFFB74D) else Color(0xFFEF6C00)
    Tier.ACCOUNT -> if (dark) Color(0xFFFFE082) else Color(0xFFF9A825)
    Tier.LOW -> if (dark) Color(0xFF90CAF9) else Color(0xFF1E88E5)
    Tier.TRASH -> if (dark) Color(0xFFB0BEC5) else Color(0xFF78909C)
}

@Composable
fun Tier.accent(): Color = color(MaterialTheme.colorScheme.isDark())

@Composable
fun androidx.compose.material3.ColorScheme.isDark(): Boolean = background.luminance() < 0.5f

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
fun AufmachenTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
