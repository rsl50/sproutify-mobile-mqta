package app.vereda.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF14212B)
private val Copper = Color(0xFFC46B3A)
private val Mist = Color(0xFFF4F1EA)
private val Tide = Color(0xFF1B3A4B)

private val LightColors = lightColorScheme(
    primary = Tide,
    onPrimary = Color.White,
    secondary = Copper,
    onSecondary = Color.White,
    background = Mist,
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB9C9),
    onPrimary = Ink,
    secondary = Color(0xFFE0A27C),
    background = Ink,
    surface = Color(0xFF1C2C36),
    onBackground = Mist,
    onSurface = Mist,
)

@Composable
fun VeredaTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
