package com.agroai.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Raw Wise palette. Screens use [AgroColors] roles, never these directly. */
object Palette {
    val Primary = Color(0xFF9FE870)
    val OnPrimary = Color(0xFF0E0F0C)
    val PrimaryActive = Color(0xFFCDFFAD)
    val PrimaryNeutral = Color(0xFFC5EDAB)
    val PrimaryPale = Color(0xFFE2F6D5)
    val Ink = Color(0xFF0E0F0C)
    val InkDeep = Color(0xFF163300)
    val Body = Color(0xFF454745)
    val Mute = Color(0xFF868685)
    val Canvas = Color(0xFFFFFFFF)
    val CanvasSoft = Color(0xFFE8EBE6)
    val Positive = Color(0xFF2EAD4B)
    val PositiveDeep = Color(0xFF054D28)
    val Warning = Color(0xFFFFD11A)
    val WarningDeep = Color(0xFFB86700)
    val WarningContent = Color(0xFF4A3B1C)
    val Negative = Color(0xFFD03238)
    val NegativeDeep = Color(0xFFA72027)
    val NegativeBg = Color(0xFF320707)
    val AccentOrange = Color(0xFFFFC091)
    val AccentCyan = Color(0xFF38C8FF)
}

/** Semantic colour roles. Light follows the Wise spec; dark keeps lime as the CTA on an ink canvas. */
@Immutable
data class AgroColors(
    val background: Color,      // screen canvas (sage)
    val surface: Color,         // cards, sheets (white)
    val surfaceSoft: Color,     // sage fill inside white surfaces
    val secondaryFill: Color,   // secondary buttons and chips on the sage canvas
    val divider: Color,
    val ink: Color,
    val body: Color,
    val mute: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryActive: Color,
    val primaryPale: Color,
    val heroDark: Color,        // dark feature cards / healthy hero
    val onHeroDark: Color,
    val positive: Color,
    val positiveDeep: Color,
    val positivePale: Color,
    val warning: Color,
    val warningContent: Color,
    val warningPale: Color,
    val negative: Color,
    val negativeDeep: Color,
    val negativePale: Color,
    val snackbar: Color,
    val onSnackbar: Color,
    val isDark: Boolean,
)

val LightAgroColors = AgroColors(
    background = Palette.CanvasSoft,
    surface = Palette.Canvas,
    surfaceSoft = Palette.CanvasSoft,
    secondaryFill = Color(0xFFD8DDD4),
    divider = Palette.CanvasSoft,
    ink = Palette.Ink,
    body = Palette.Body,
    mute = Palette.Mute,
    primary = Palette.Primary,
    onPrimary = Palette.OnPrimary,
    primaryActive = Palette.PrimaryActive,
    primaryPale = Palette.PrimaryPale,
    heroDark = Palette.Ink,
    onHeroDark = Palette.Primary,
    positive = Palette.Positive,
    positiveDeep = Palette.PositiveDeep,
    positivePale = Palette.PrimaryPale,
    warning = Palette.Warning,
    warningContent = Palette.WarningContent,
    warningPale = Color(0xFFFFF4CC),
    negative = Palette.Negative,
    negativeDeep = Palette.NegativeDeep,
    negativePale = Color(0xFFFBE3E3),
    snackbar = Palette.Ink,
    onSnackbar = Palette.Canvas,
    isDark = false,
)

val DarkAgroColors = AgroColors(
    background = Palette.Ink,
    surface = Color(0xFF1C211A),
    surfaceSoft = Color(0xFF262C23),
    secondaryFill = Color(0xFF2A3127),
    divider = Color(0xFF2A3127),
    ink = Color(0xFFF1F3EF),
    body = Color(0xFFBEC2BB),
    mute = Color(0xFF8E918C),
    primary = Palette.Primary,
    onPrimary = Palette.OnPrimary,
    primaryActive = Palette.PrimaryActive,
    primaryPale = Color(0xFF243A18),
    heroDark = Color(0xFF243A18),
    onHeroDark = Palette.Primary,
    positive = Color(0xFF5BD077),
    positiveDeep = Palette.PrimaryActive,
    positivePale = Color(0xFF243A18),
    warning = Palette.Warning,
    warningContent = Color(0xFFFFE27A),
    warningPale = Color(0xFF3A3012),
    negative = Color(0xFFFF6B70),
    negativeDeep = Color(0xFFFFA3A6),
    negativePale = Color(0xFF3A1415),
    snackbar = Color(0xFFF1F3EF),
    onSnackbar = Palette.Ink,
    isDark = true,
)

val LocalAgroColors = staticCompositionLocalOf { LightAgroColors }
