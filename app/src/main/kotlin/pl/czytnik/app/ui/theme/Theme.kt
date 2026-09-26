package pl.czytnik.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Motyw wysokiego kontrastu (docs/TECH-SPEC.md, rozdz. 5.2): wszystkie pary kolorów ≥ 7:1.
val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)
val Yellow = Color(0xFFFFD600)

private val HighContrastColors = darkColorScheme(
    primary = Yellow,
    onPrimary = Black,
    secondary = White,
    onSecondary = Black,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    error = Yellow,
    onError = Black,
)

private val LargeTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 28.sp, lineHeight = 36.sp),
    bodyMedium = TextStyle(fontSize = 22.sp, lineHeight = 30.sp),
    labelLarge = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun CzytnikTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HighContrastColors,
        typography = LargeTypography,
        content = content,
    )
}
