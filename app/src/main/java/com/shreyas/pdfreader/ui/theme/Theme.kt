package com.shreyas.pdfreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.shreyas.pdfreader.data.PageTheme

private val Ink = Color(0xFF1F1B16)
private val Paper = Color(0xFFFFFFFF)
private val Sepia = Color(0xFFF4ECD8)
private val SepiaDeep = Color(0xFFE9DFC6)
private val Accent = Color(0xFF8A5A2B)
private val AccentOnDark = Color(0xFFE0B98A)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceContainer = Color(0xFFF6F3EE),
    surfaceContainerHigh = Color(0xFFF0ECE5),
    surfaceContainerLow = Color(0xFFFAF8F4),
)

private val DarkColors = darkColorScheme(
    primary = AccentOnDark,
    onPrimary = Ink,
    background = Color.Black,
    onBackground = Color(0xFFE8E2D9),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE8E2D9),
    surfaceContainer = Color(0xFF1C1B1A),
    surfaceContainerHigh = Color(0xFF262422),
    surfaceContainerLow = Color(0xFF171615),
)

private val SepiaColors = LightColors.copy(
    background = Sepia,
    surface = Sepia,
    surfaceContainer = SepiaDeep,
    surfaceContainerHigh = SepiaDeep,
    surfaceContainerLow = Sepia,
)

/** Library and dialogs. Follows the system setting. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

/** Reader controls. Follows the page theme, so the bars match the page. */
@Composable
fun ReaderTheme(theme: PageTheme, content: @Composable () -> Unit) {
    val colors = when (theme) {
        PageTheme.LIGHT -> LightColors
        PageTheme.DARK -> DarkColors
        PageTheme.SEPIA -> SepiaColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/**
 * Page themes are filters over the rendered bitmap. A PDF holds fixed colours, so true recolouring
 * is not possible with this renderer. Dark mode inverts photos and diagrams together with the text.
 */
class PagePalette(val background: Color, val filter: ColorFilter?)

private val InvertColors = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

val PageTheme.palette: PagePalette
    get() = when (this) {
        PageTheme.LIGHT -> PagePalette(Color(0xFFECEAE6), null)
        PageTheme.DARK -> PagePalette(Color.Black, ColorFilter.colorMatrix(InvertColors))
        PageTheme.SEPIA -> PagePalette(SepiaDeep, ColorFilter.tint(Sepia, BlendMode.Multiply))
    }
