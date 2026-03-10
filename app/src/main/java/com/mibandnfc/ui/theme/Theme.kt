package com.mibandnfc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = XiaomiOrange,
    onPrimary = Color.White,
    primaryContainer = XiaomiOrangeContainer,
    onPrimaryContainer = OnXiaomiOrangeContainer,
    secondary = BlueAccent,
    onSecondary = Color.White,
    secondaryContainer = BlueAccentContainer,
    onSecondaryContainer = OnBlueAccentContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = ErrorRed,
    errorContainer = ErrorRedContainer,
    onErrorContainer = OnErrorRedContainer,
)

private val DarkColorScheme = darkColorScheme(
    primary = XiaomiOrangeLight,
    onPrimary = Color(0xFF5C2700),
    primaryContainer = XiaomiOrangeDark,
    onPrimaryContainer = XiaomiOrangeContainer,
    secondary = BlueAccentDark,
    onSecondary = Color(0xFF003258),
    secondaryContainer = Color(0xFF004880),
    onSecondaryContainer = BlueAccentContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = ErrorRedDark,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = ErrorRedContainer,
)

@Composable
fun MiBandNfcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
