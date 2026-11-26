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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.data.AccentSwatch
import com.example.data.ClockNumberStyle
import com.example.data.FloatingPillStyle
import com.example.data.GaugeStyle
import com.example.data.ThemeMode

@Immutable
data class AKitDesignTokens(
    val gaugeStyle: GaugeStyle = GaugeStyle.MINIMAL_THIN_ARC,
    val floatingPillStyle: FloatingPillStyle = FloatingPillStyle.COMPACT_SINGLE_LINE,
    val clockNumberStyle: ClockNumberStyle = ClockNumberStyle.EDITORIAL_SERIF,
    val accentSwatch: AccentSwatch = AccentSwatch.ELECTRIC_BLUE,
    val chargingAccent: Color = ElectricBluePrimary,
    val dischargingAccent: Color = CoralPeach,
    val use24HourTime: Boolean = false,
)

val LocalAKitDesign = staticCompositionLocalOf { AKitDesignTokens() }

private val AKitOliveDarkScheme = darkColorScheme(
    primary = MintEmerald,
    onPrimary = OnMintEmerald,
    primaryContainer = MintEmeraldContainer,
    onPrimaryContainer = OnMintEmeraldContainer,
    secondary = MintSignal,
    onSecondary = OnMintSignal,
    secondaryContainer = MintContainerDark,
    onSecondaryContainer = OnMintContainerDark,
    tertiary = CoralPeach,
    onTertiary = OnCoralPeach,
    tertiaryContainer = CoralPeachContainer,
    onTertiaryContainer = OnCoralPeachContainer,
    error = CoralErrorDark,
    onError = OnCoralErrorDark,
    errorContainer = CoralErrorContainerDark,
    onErrorContainer = OnCoralErrorContainerDark,
    background = OliveCharcoalBg,
    onBackground = TextPrimaryDark,
    surface = OliveCharcoalBg,
    onSurface = TextPrimaryDark,
    surfaceVariant = OliveCharcoalHigh,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = OliveCharcoalLowest,
    surfaceContainerLow = OliveCharcoalCard,
    surfaceContainer = OliveCharcoalCard,
    surfaceContainerHigh = OliveCharcoalHigh,
    surfaceContainerHighest = OliveCharcoalHighest,
    outline = OliveCharcoalOutline,
    outlineVariant = OliveCharcoalOutlineVariant,
)

private val AKitAmoledScheme = AKitOliveDarkScheme.copy(
    background = AmoledBlack,
    surface = AmoledBlack,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = AmoledSurfaceLow,
    surfaceContainer = AmoledSurface,
    surfaceContainerHigh = AmoledSurfaceHigh,
    surfaceContainerHighest = AmoledSurfaceHighest,
    outline = AmoledOutline,
    outlineVariant = AmoledOutlineVariant,
)

private val AKitLightScheme = lightColorScheme(
    primary = MintLight,
    onPrimary = OnMintLight,
    primaryContainer = MintContainerLight,
    onPrimaryContainer = OnMintContainerLight,
    secondary = MintLight,
    onSecondary = OnMintLight,
    secondaryContainer = MintContainerLight,
    onSecondaryContainer = OnMintContainerLight,
    tertiary = PeachLight,
    onTertiary = OnPeachLight,
    tertiaryContainer = PeachContainerLight,
    onTertiaryContainer = OnPeachContainerLight,
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

private fun ColorScheme.withOliveDarkSurfaces(): ColorScheme = copy(
    background = OliveCharcoalBg,
    surface = OliveCharcoalBg,
    surfaceContainerLowest = OliveCharcoalLowest,
    surfaceContainerLow = OliveCharcoalCard,
    surfaceContainer = OliveCharcoalCard,
    surfaceContainerHigh = OliveCharcoalHigh,
    surfaceContainerHighest = OliveCharcoalHighest,
    outline = OliveCharcoalOutline,
    outlineVariant = OliveCharcoalOutlineVariant,
)

private fun ColorScheme.withAmoledSurfaces(): ColorScheme = copy(
    background = AmoledBlack,
    surface = AmoledBlack,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = AmoledSurfaceLow,
    surfaceContainer = AmoledSurface,
    surfaceContainerHigh = AmoledSurfaceHigh,
    surfaceContainerHighest = AmoledSurfaceHighest,
    outline = AmoledOutline,
    outlineVariant = AmoledOutlineVariant,
)

private fun blendDarkContainer(accent: Color): Color = Color(
    red = (accent.red * 0.22f + 0.05f).coerceIn(0f, 1f),
    green = (accent.green * 0.22f + 0.06f).coerceIn(0f, 1f),
    blue = (accent.blue * 0.22f + 0.07f).coerceIn(0f, 1f),
    alpha = 1f,
)

private fun blendLightContainer(accent: Color): Color = Color(
    red = (accent.red * 0.22f + 0.78f).coerceIn(0f, 1f),
    green = (accent.green * 0.22f + 0.78f).coerceIn(0f, 1f),
    blue = (accent.blue * 0.22f + 0.78f).coerceIn(0f, 1f),
    alpha = 1f,
)

private fun ColorScheme.withAccentSwatch(swatch: AccentSwatch, darkTheme: Boolean): ColorScheme {
    if (swatch == AccentSwatch.DYNAMIC_AUTO) return this
    val rawAccent = Color(swatch.argbInt)

    if (!darkTheme) {
        val lightPrimary = if (swatch == AccentSwatch.PURE_WHITE) {
            Color(0xFF2B3137)
        } else {
            Color(
                red = (rawAccent.red * 0.68f).coerceIn(0f, 1f),
                green = (rawAccent.green * 0.68f).coerceIn(0f, 1f),
                blue = (rawAccent.blue * 0.68f).coerceIn(0f, 1f),
                alpha = 1f,
            )
        }
        return copy(
            primary = lightPrimary,
            onPrimary = Color.White,
            primaryContainer = blendLightContainer(lightPrimary),
            onPrimaryContainer = Color(0xFF10151C),
        )
    }

    val onPrimary = if (rawAccent.luminance() > 0.35f) Color(0xFF0A1016) else Color.White
    val container = if (swatch == AccentSwatch.PURE_WHITE) MonochromeContainer else blendDarkContainer(rawAccent)
    val onContainer = if (swatch == AccentSwatch.PURE_WHITE) OnMonochromeContainer else Color(
        red = (rawAccent.red * 0.35f + 0.65f).coerceIn(0f, 1f),
        green = (rawAccent.green * 0.35f + 0.65f).coerceIn(0f, 1f),
        blue = (rawAccent.blue * 0.35f + 0.65f).coerceIn(0f, 1f),
        alpha = 1f,
    )
    return copy(
        primary = rawAccent,
        onPrimary = onPrimary,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
    )
}

@Composable
fun AKitTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    gaugeStyle: GaugeStyle = GaugeStyle.MINIMAL_THIN_ARC,
    floatingPillStyle: FloatingPillStyle = FloatingPillStyle.COMPACT_SINGLE_LINE,
    clockNumberStyle: ClockNumberStyle = ClockNumberStyle.EDITORIAL_SERIF,
    accentSwatch: AccentSwatch = AccentSwatch.ELECTRIC_BLUE,
    use24HourTime: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val useAmoledSurface = themeMode == ThemeMode.AMOLED

    val context = LocalContext.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useSystemDynamic = supportsDynamicColor && (dynamicColor || accentSwatch == AccentSwatch.DYNAMIC_AUTO)

    val baseScheme = when {
        useSystemDynamic -> {
            val dynamicBase = if (useDarkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
            when {
                useAmoledSurface -> dynamicBase.withAmoledSurfaces()
                useDarkTheme -> dynamicBase.withOliveDarkSurfaces()
                else -> dynamicBase
            }
        }
        useAmoledSurface -> AKitAmoledScheme
        useDarkTheme -> AKitOliveDarkScheme
        else -> AKitLightScheme
    }

    val colorScheme = baseScheme.withAccentSwatch(accentSwatch, useDarkTheme)

    val chargingAccent = when (accentSwatch) {
        AccentSwatch.DYNAMIC_AUTO -> if (useSystemDynamic) colorScheme.primary else if (useDarkTheme) MintEmerald else MintLight
        AccentSwatch.PURE_WHITE -> colorScheme.primary
        else -> Color(accentSwatch.argbInt)
    }

    val dischargingAccent = when (accentSwatch) {
        AccentSwatch.PURE_WHITE -> colorScheme.onSurfaceVariant
        AccentSwatch.CORAL_RED -> Color(AccentSwatch.CORAL_RED.argbInt)
        AccentSwatch.AMBER -> Color(AccentSwatch.AMBER.argbInt)
        else -> if (useDarkTheme) CoralPeach else PeachLight
    }

    val designTokens = AKitDesignTokens(
        gaugeStyle = gaugeStyle,
        floatingPillStyle = floatingPillStyle,
        clockNumberStyle = clockNumberStyle,
        accentSwatch = accentSwatch,
        chargingAccent = chargingAccent,
        dischargingAccent = dischargingAccent,
        use24HourTime = use24HourTime,
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !useDarkTheme
            insetsController.isAppearanceLightNavigationBars = !useDarkTheme
        }
    }

    CompositionLocalProvider(LocalAKitDesign provides designTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AKitTypography,
            shapes = AKitShapes,
            content = content,
        )
    }
}
