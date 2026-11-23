package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.data.ThemeMode

private val AKitAmoledFallbackScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = OnCyanPulse,
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = OnCyanContainerDark,
    secondary = MintSignal,
    onSecondary = OnMintSignal,
    secondaryContainer = MintContainerDark,
    onSecondaryContainer = OnMintContainerDark,
    tertiary = AmberVolt,
    onTertiary = OnAmberVolt,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    error = CoralErrorDark,
    onError = OnCoralErrorDark,
    errorContainer = CoralErrorContainerDark,
    onErrorContainer = OnCoralErrorContainerDark,
    background = AmoledBlack,
    onBackground = TextPrimaryDark,
    surface = AmoledBlack,
    onSurface = TextPrimaryDark,
    surfaceVariant = AmoledSurfaceHigh,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = AmoledSurfaceLow,
    surfaceContainer = AmoledSurface,
    surfaceContainerHigh = AmoledSurfaceHigh,
    surfaceContainerHighest = AmoledSurfaceHighest,
    outline = AmoledOutline,
    outlineVariant = AmoledOutlineVariant,
)

private val AKitDarkFallbackScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = OnCyanPulse,
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = OnCyanContainerDark,
    secondary = MintSignal,
    onSecondary = OnMintSignal,
    secondaryContainer = MintContainerDark,
    onSecondaryContainer = OnMintContainerDark,
    tertiary = AmberVolt,
    onTertiary = OnAmberVolt,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    error = CoralErrorDark,
    onError = OnCoralErrorDark,
    errorContainer = CoralErrorContainerDark,
    onErrorContainer = OnCoralErrorContainerDark,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkBackground,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
    outline = AmoledOutline,
    outlineVariant = AmoledOutlineVariant,
)

private val AKitLightFallbackScheme = lightColorScheme(
    primary = CyanLight,
    onPrimary = OnCyanLight,
    primaryContainer = CyanContainerLight,
    onPrimaryContainer = OnCyanContainerLight,
    secondary = MintLight,
    onSecondary = OnMintLight,
    secondaryContainer = MintContainerLight,
    onSecondaryContainer = OnMintContainerLight,
    tertiary = AmberLight,
    onTertiary = OnAmberLight,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = OnAmberContainerLight,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightBackground,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = LightSurfaceLowest,
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

private fun ColorScheme.withAmoledSurfaces(): ColorScheme = copy(
    background = AmoledBlack,
    surface = AmoledBlack,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = AmoledSurfaceLow,
    surfaceContainer = AmoledSurface,
    surfaceContainerHigh = AmoledSurfaceHigh,
    surfaceContainerHighest = AmoledSurfaceHighest,
    outlineVariant = AmoledOutlineVariant,
)

@Composable
fun AKitTheme(
    themeMode: ThemeMode = ThemeMode.AMOLED,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val useAmoledSurface = themeMode == ThemeMode.AMOLED || (themeMode == ThemeMode.SYSTEM && systemDark)

    val context = LocalContext.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        dynamicColor && supportsDynamicColor -> {
            val base = if (useDarkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
            if (useAmoledSurface) base.withAmoledSurfaces() else base
        }

        useAmoledSurface -> AKitAmoledFallbackScheme
        useDarkTheme -> AKitDarkFallbackScheme
        else -> AKitLightFallbackScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !useDarkTheme
            insetsController.isAppearanceLightNavigationBars = !useDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AKitTypography,
        shapes = AKitShapes,
        content = content,
    )
}
