@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.daniel.mood.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import com.daniel.mood.MoodCatalog
import com.daniel.mood.R

val Navy = Color(0xFF0C0F27)
val SurfaceDark = Color(0xFF151938)
val SurfaceDarkAlt = Color(0xFF20244B)
val TextPrimary = Color(0xFFF3F4FF)
val TextMuted = Color(0xFFA7ADC9)
val AccentPrimary = Color(0xFF8B7CFF)
val AccentCyan = Color(0xFF3EE8D1)

private val DarkColors = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = Color.White,
    secondary = AccentCyan,
    onSecondary = Navy,
    background = Navy,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDarkAlt,
    onSurfaceVariant = TextPrimary,
    outline = Color(0xFF454A73)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4BD6),
    onPrimary = Color.White,
    secondary = Color(0xFF008F7A),
    background = Color(0xFFF5F6FF),
    onBackground = Color(0xFF171A35),
    surface = Color.White,
    onSurface = Color(0xFF171A35),
    surfaceVariant = Color(0xFFE9EBFA),
    outline = Color(0xFFB8BCD8)
)

fun moodColor(id: Int): Color = Color(MoodCatalog.color(id))

fun moodBrush(accent: Int, dark: Boolean): Brush = Brush.verticalGradient(
    listOf(
        moodColor(accent).copy(alpha = if (dark) 0.30f else 0.20f),
        moodColor(accent).copy(alpha = if (dark) 0.07f else 0.04f),
        if (dark) Navy else Color(0xFFF5F6FF)
    )
)

@Composable
fun MoodMusicTheme(dark: Boolean, persian: Boolean, content: @Composable () -> Unit) {
    // The bundled DejaVu font covers Persian glyphs; system fallback shapes them.
    val font = FontFamily(Font(R.font.persian))
    val typography = Typography().let { base ->
        base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = font),
            displayMedium = base.displayMedium.copy(fontFamily = font),
            headlineMedium = base.headlineMedium.copy(fontFamily = font),
            titleLarge = base.titleLarge.copy(fontFamily = font),
            titleMedium = base.titleMedium.copy(fontFamily = font),
            titleSmall = base.titleSmall.copy(fontFamily = font),
            bodyLarge = base.bodyLarge.copy(fontFamily = font),
            bodyMedium = base.bodyMedium.copy(fontFamily = font),
            bodySmall = base.bodySmall.copy(fontFamily = font),
            labelLarge = base.labelLarge.copy(fontFamily = font),
            labelMedium = base.labelMedium.copy(fontFamily = font),
            labelSmall = base.labelSmall.copy(fontFamily = font)
        )
    }
    CompositionLocalProvider(
        LocalLayoutDirection provides if (persian) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = typography,
            content = content
        )
    }
}
