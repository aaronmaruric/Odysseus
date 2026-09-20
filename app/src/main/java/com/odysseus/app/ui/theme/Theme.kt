package com.odysseus.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Nothing-OS-inspired palette: strictly monochrome surfaces, one red accent, no tonal tints.
 * Dark mode is true black; light mode is paper white. Everything that would normally be a
 * "primary container" tint is instead a hairline border or a grey.
 */
private val Black = Color(0xFF000000)
private val White = Color(0xFFFFFFFF)
private val Grey900 = Color(0xFF121212)
private val Grey800 = Color(0xFF1E1E1E)
private val Grey600 = Color(0xFF5C5C5C)
private val Grey400 = Color(0xFF9A9A9A)
private val Grey200 = Color(0xFFD9D9D9)
private val Grey100 = Color(0xFFF2F2F2)

/** The single accent. Nothing red. */
val NothingRed = Color(0xFFD71921)

/** Calendar marker for runs — the accent. */
val RunColor = NothingRed

/** Calendar marker for strength — a neutral that reads on both backgrounds. */
val StrengthColor = Grey400

private val DarkColors = darkColorScheme(
    primary = White,
    onPrimary = Black,
    primaryContainer = Grey800,
    onPrimaryContainer = White,
    secondary = Grey400,
    onSecondary = Black,
    tertiary = NothingRed,
    onTertiary = White,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = Grey900,
    onSurfaceVariant = Grey400,
    surfaceContainer = Grey900,
    surfaceContainerHigh = Grey800,
    outline = Grey600,
    outlineVariant = Grey800,
    error = NothingRed,
    onError = White,
)

private val LightColors = lightColorScheme(
    primary = Black,
    onPrimary = White,
    primaryContainer = Grey100,
    onPrimaryContainer = Black,
    secondary = Grey600,
    onSecondary = White,
    tertiary = NothingRed,
    onTertiary = White,
    background = White,
    onBackground = Black,
    surface = White,
    onSurface = Black,
    surfaceVariant = Grey100,
    onSurfaceVariant = Grey600,
    surfaceContainer = Grey100,
    surfaceContainerHigh = Grey200,
    outline = Grey400,
    outlineVariant = Grey200,
    error = NothingRed,
    onError = White,
)

/*
 * Nothing uses a dot-matrix display face (NDot) for numerals and headings. That font is not
 * freely licensed, so we approximate the feel with a monospace face for anything numeric or
 * heading-like, and keep body text in the default sans. Swap [Display] for a bundled font
 * (e.g. an OFL dot-matrix face in res/font) if you want the real look.
 */
val Display: FontFamily = FontFamily.Monospace

private val Base = Typography()

val NothingTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = Display, fontWeight = FontWeight.Normal),
    displayMedium = Base.displayMedium.copy(fontFamily = Display, fontWeight = FontWeight.Normal),
    displaySmall = Base.displaySmall.copy(fontFamily = Display, fontWeight = FontWeight.Normal),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Display, letterSpacing = 1.sp),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Display, letterSpacing = 1.sp),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Display, letterSpacing = 1.sp),
    titleLarge = Base.titleLarge.copy(fontFamily = Display, letterSpacing = 2.sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Medium),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
    labelMedium = Base.labelMedium.copy(letterSpacing = 1.5.sp),
    labelSmall = Base.labelSmall.copy(fontFamily = Display),
)

/** Style for big numbers (distance, pace, elapsed time). */
val Numeral: TextStyle = TextStyle(fontFamily = Display, fontSize = 40.sp, letterSpacing = 1.sp)

/** Nothing leans on circles and pills; tight corners elsewhere. */
val NothingShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(50),
)

@Composable
fun OdysseusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = NothingTypography,
        shapes = NothingShapes,
        content = content,
    )
}
