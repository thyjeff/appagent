package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = AntiqueGoldDarkPrimary,
    onPrimary = Color(0xFF2E1500),
    primaryContainer = GoldContainerDark,
    onPrimaryContainer = Color(0xFFFDE68A),
    secondary = EspressoDarkSecondary,
    onSecondary = Color(0xFF1C1917),
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = Color(0xFFE7E5E4),
    tertiary = SageDarkTertiary,
    background = ObsidianDarkBackground,
    onBackground = ParchmentOnDark,
    surface = ObsidianDarkSurface,
    onSurface = ParchmentOnDark,
    surfaceVariant = ObsidianDarkSurfaceVariant,
    onSurfaceVariant = MutedParchmentOnDark,
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF27272A),
    error = CrimsonKillSwitch
)

private val LightColorScheme = lightColorScheme(
    primary = AntiqueGoldPrimary,
    onPrimary = Color.White,
    primaryContainer = GoldContainerLight,
    onPrimaryContainer = Color(0xFF78350F),
    secondary = EspressoSecondary,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = Color(0xFF292524),
    tertiary = SageTertiary,
    background = ParchmentLightBackground,
    onBackground = EspressoOnLight,
    surface = ParchmentLightSurface,
    onSurface = EspressoOnLight,
    surfaceVariant = ParchmentLightSurfaceVariant,
    onSurfaceVariant = MutedEspressoOnLight,
    outline = BorderParchmentLight,
    outlineVariant = Color(0xFFEDE9E3),
    error = CrimsonKillSwitch
)

@Composable
fun FocusPilotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // false by default to showcase the tailored elegant editorial theme
    customPrimaryColor: Color? = null,
    content: @Composable () -> Unit
) {
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val colorScheme = if (customPrimaryColor != null) {
        baseScheme.copy(
            primary = customPrimaryColor,
            primaryContainer = customPrimaryColor.copy(alpha = if (darkTheme) 0.35f else 0.15f),
            onPrimaryContainer = if (darkTheme) customPrimaryColor.copy(alpha = 0.9f) else customPrimaryColor
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
