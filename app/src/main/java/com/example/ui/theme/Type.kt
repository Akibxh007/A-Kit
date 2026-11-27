package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val MontserratAlternatesFontFamily = FontFamily(
    Font(R.font.montserrat_alternates, FontWeight.Normal),
    Font(R.font.montserrat_alternates, FontWeight.Medium),
    Font(R.font.montserrat_alternates, FontWeight.SemiBold),
    Font(R.font.montserrat_alternates, FontWeight.Bold),
    Font(R.font.montserrat_alternates, FontWeight.ExtraBold),
)

val RobotoCondensedFontFamily = FontFamily(
    Font(R.font.roboto_condensed, FontWeight.Normal),
    Font(R.font.roboto_condensed, FontWeight.Medium),
    Font(R.font.roboto_condensed, FontWeight.SemiBold),
    Font(R.font.roboto_condensed, FontWeight.Bold),
)

val BodoniModaFontFamily = FontFamily(
    Font(R.font.bodoni_moda, FontWeight.Normal),
    Font(R.font.bodoni_moda, FontWeight.Medium),
    Font(R.font.bodoni_moda, FontWeight.SemiBold),
    Font(R.font.bodoni_moda, FontWeight.Bold),
    Font(R.font.bodoni_moda, FontWeight.ExtraBold),
)

val StardosStencilFontFamily = FontFamily(
    Font(R.font.stardos_stencil, FontWeight.Normal),
    Font(R.font.stardos_stencil, FontWeight.Bold),
)

val JostFontFamily = FontFamily(
    Font(R.font.jost, FontWeight.Thin),
    Font(R.font.jost, FontWeight.ExtraLight),
    Font(R.font.jost, FontWeight.Light),
    Font(R.font.jost, FontWeight.Normal),
    Font(R.font.jost, FontWeight.Medium),
)

val CinzelDecorativeFontFamily = FontFamily(
    Font(R.font.cinzel_decorative, FontWeight.Normal),
    Font(R.font.cinzel_decorative, FontWeight.Bold),
    Font(R.font.cinzel_decorative, FontWeight.ExtraBold),
)

val FredokaFontFamily = FontFamily(
    Font(R.font.fredoka, FontWeight.Medium),
    Font(R.font.fredoka, FontWeight.SemiBold),
    Font(R.font.fredoka, FontWeight.Bold),
    Font(R.font.fredoka, FontWeight.ExtraBold),
)

val OrbitronFontFamily = FontFamily(
    Font(R.font.orbitron, FontWeight.Medium),
    Font(R.font.orbitron, FontWeight.SemiBold),
    Font(R.font.orbitron, FontWeight.Bold),
    Font(R.font.orbitron, FontWeight.ExtraBold),
)

val AbrilFatfaceFontFamily = FontFamily(
    Font(R.font.abril_fatface, FontWeight.Normal),
    Font(R.font.abril_fatface, FontWeight.Bold),
    Font(R.font.abril_fatface, FontWeight.ExtraBold),
)

val CleanDateSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
)

// Compatibility aliases mapped cleanly to our new font pairing
val SpaceGroteskFontFamily = MontserratAlternatesFontFamily
val PlusJakartaSansFontFamily = RobotoCondensedFontFamily
val JetBrainsMonoFontFamily = RobotoCondensedFontFamily

val MonoMetricStyle = TextStyle(
    fontFamily = MontserratAlternatesFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.5).sp,
)

val MonoLabelStyle = TextStyle(
    fontFamily = RobotoCondensedFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 11.5.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.8.sp,
)

val AKitTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = MontserratAlternatesFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 46.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1.0).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = MontserratAlternatesFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = MontserratAlternatesFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = MontserratAlternatesFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = MontserratAlternatesFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.15).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = MontserratAlternatesFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = MontserratAlternatesFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = MontserratAlternatesFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = RobotoCondensedFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = RobotoCondensedFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = RobotoCondensedFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = RobotoCondensedFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = RobotoCondensedFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = RobotoCondensedFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.35.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = RobotoCondensedFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp,
    ),
)
