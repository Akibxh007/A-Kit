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
    val clockNumberStyle: ClockNumberStyle = ClockNumberStyle.MONTSERRAT_BOLD,
    val accentSwatch: AccentSwatch = AccentSwatch.ELECTRIC_BLUE,
    val chargingAccent: Color = ElectricBluePrimary,
    val dischargingAccent: Color = CoralPeach,
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

private fun ColorScheme.withAccentSwatch(swatch: AccentSwatch, darkTheme: Boolean): ColorScheme {
    if (!darkTheme) {
        return when (swatch) {
            AccentSwatch.DYNAMIC_AUTO -> this
            AccentSwatch.EMERALD_GREEN -> copy(
                primary = Color(0xFF127A43),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFBBF7D0),
                onPrimaryContainer = Color(0xFF042613),
            )
            AccentSwatch.CYBER_PEACH -> copy(
                primary = Color(0xFFC2571A),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFFFE0C8),
                onPrimaryContainer = Color(0xFF3B1500),
            )
            AccentSwatch.ELECTRIC_BLUE -> copy(
                primary = Color(0xFF026AA7),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFC6EBFF),
                onPrimaryContainer = Color(0xFF001F33),
            )
            AccentSwatch.MONOCHROME -> copy(
                primary = Color(0xFF2B3137),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFDCE1E7),
                onPrimaryContainer = Color(0xFF111418),
            )
        }
    }
    return when (swatch) {
        AccentSwatch.DYNAMIC_AUTO -> this
        AccentSwatch.EMERALD_GREEN -> copy(
            primary = EmeraldSwatchPrimary,
            onPrimary = Color(0xFF032914),
            primaryContainer = EmeraldSwatchContainer,
            onPrimaryContainer = OnEmeraldSwatchContainer,
            secondary = EmeraldSwatchPrimary,
            secondaryContainer = EmeraldSwatchContainer,
            onSecondaryContainer = OnEmeraldSwatchContainer,
        )
        AccentSwatch.CYBER_PEACH -> copy(
            primary = CyberPeachPrimary,
            onPrimary = Color(0xFF3E1904),
            primaryContainer = CyberPeachContainer,
            onPrimaryContainer = OnCyberPeachContainer,
        )
        AccentSwatch.ELECTRIC_BLUE -> copy(
            primary = ElectricBluePrimary,
            onPrimary = Color(0xFF022638),
            primaryContainer = ElectricBlueContainer,
            onPrimaryContainer = OnElectricBlueContainer,
        )
        AccentSwatch.MONOCHROME -> copy(
            primary = MonochromePrimary,
            onPrimary = Color(0xFF111418),
            primaryContainer = MonochromeContainer,
            onPrimaryContainer = OnMonochromeContainer,
            secondary = Color(0xFFCBD5E1),
            secondaryContainer = MonochromeContainer,
            onSecondaryContainer = OnMonochromeContainer,
        )
    }
}

@Composable
fun AKitTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    gaugeStyle: GaugeStyle = GaugeStyle.MINIMAL_THIN_ARC,
    floatingPillStyle: FloatingPillStyle = FloatingPillStyle.COMPACT_SINGLE_LINE,
    clockNumberStyle: ClockNumberStyle = ClockNumberStyle.MONTSERRAT_BOLD,
    accentSwatch: AccentSwatch = AccentSwatch.ELECTRIC_BLUE,
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
        AccentSwatch.DYNAMIC_AUTO -> if (useDarkTheme) MintEmerald else MintLight
        AccentSwatch.EMERALD_GREEN -> EmeraldSwatchPrimary
        AccentSwatch.CYBER_PEACH -> CyberPeachPrimary
        AccentSwatch.ELECTRIC_BLUE -> ElectricBluePrimary
        AccentSwatch.MONOCHROME -> colorScheme.primary
    }

    val dischargingAccent = when (accentSwatch) {
        AccentSwatch.MONOCHROME -> colorScheme.onSurfaceVariant
        AccentSwatch.CYBER_PEACH -> CyberPeachPrimary
        else -> if (useDarkTheme) CoralPeach else PeachLight
    }

    val designTokens = AKitDesignTokens(
        gaugeStyle = gaugeStyle,
        floatingPillStyle = floatingPillStyle,
        clockNumberStyle = clockNumberStyle,
        accentSwatch = accentSwatch,
        chargingAccent = chargingAccent,
        dischargingAccent = dischargingAccent,
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
