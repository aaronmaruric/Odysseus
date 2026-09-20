package com.odysseus.app.ui.theme

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

private val Navy = Color(0xFF1B4965)
private val Teal = Color(0xFF5FA8D3)
private val Sand = Color(0xFFCAE9FF)

private val LightColors = lightColorScheme(
    primary = Navy,
    secondary = Teal,
    tertiary = Sand,
)

private val DarkColors = darkColorScheme(
    primary = Teal,
    secondary = Sand,
    tertiary = Navy,
)

/** Colour used for run markers on the calendar. */
val RunColor = Color(0xFF2E86AB)

/** Colour used for strength markers on the calendar. */
val StrengthColor = Color(0xFFE07A5F)

@Composable
fun OdysseusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
