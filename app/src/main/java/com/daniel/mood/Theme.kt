package com.daniel.mood

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

val MoodColors = listOf(
    Color(0xFFFFC857), Color(0xFF8194EF), Color(0xFF62D9C5), Color(0xFF65B8E8),
    Color(0xFFC49AE8), Color(0xFFFF8FAF), Color(0xFFFF9B55), Color(0xFFB58CFF),
    Color(0xFFE99ADB), Color(0xFF69D3E7), Color(0xFF8992AA)
)
val Navy = Color(0xFF0B0D1C)
val CardNavy = Color(0xFF171A30)

@Composable
fun MoodMusicTheme(fa: Boolean, dark: Boolean, mood: Int = -1, content: @Composable () -> Unit) {
    val accent = MoodColors.getOrElse(mood) { Color(0xFF8D82FF) }
    val scheme = if (dark) darkColorScheme(
        primary = accent, onPrimary = Navy, secondary = Color(0xFF62D9E8),
        background = Navy, onBackground = Color(0xFFF3F2FF), surface = Navy,
        onSurface = Color(0xFFF3F2FF), surfaceVariant = CardNavy,
        onSurfaceVariant = Color(0xFFC3C3D9), outline = Color(0xFF393D59),
        error = Color(0xFFFF8D9D)
    ) else lightColorScheme(primary = accent, secondary = Color(0xFF267E94))
    val base = Typography()
    val font = FontFamily(Font(R.font.persian))
    MaterialTheme(
        colorScheme = scheme,
        typography = base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = font),
            displayMedium = base.displayMedium.copy(fontFamily = font),
            displaySmall = base.displaySmall.copy(fontFamily = font),
            headlineLarge = base.headlineLarge.copy(fontFamily = font),
            headlineMedium = base.headlineMedium.copy(fontFamily = font),
            headlineSmall = base.headlineSmall.copy(fontFamily = font),
            titleLarge = base.titleLarge.copy(fontFamily = font),
            titleMedium = base.titleMedium.copy(fontFamily = font),
            titleSmall = base.titleSmall.copy(fontFamily = font),
            bodyLarge = base.bodyLarge.copy(fontFamily = font),
            bodyMedium = base.bodyMedium.copy(fontFamily = font),
            bodySmall = base.bodySmall.copy(fontFamily = font),
            labelLarge = base.labelLarge.copy(fontFamily = font),
            labelMedium = base.labelMedium.copy(fontFamily = font),
            labelSmall = base.labelSmall.copy(fontFamily = font)
        ),
        content = content
    )
}
