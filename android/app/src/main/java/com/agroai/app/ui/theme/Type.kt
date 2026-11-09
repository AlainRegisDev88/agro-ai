package com.agroai.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Wise Sans is proprietary. Display styles use the system sans at Black (900), which on
 * Android is Roboto Black and ships on every phone, so the app stays fully offline.
 * To switch to Inter, drop the TTFs in res/font and point these two families at them.
 */
object AgroFonts {
    val Display: FontFamily = FontFamily.SansSerif
    val Text: FontFamily = FontFamily.SansSerif
}

@Immutable
data class AgroTypography(
    val displayMega: TextStyle,
    val displayXxl: TextStyle,
    val displayXl: TextStyle,
    val displayLg: TextStyle,
    val displayMd: TextStyle,
    val displaySm: TextStyle,
    val displayXs: TextStyle,
    val bodyLg: TextStyle,
    val bodyMd: TextStyle,
    val bodyMdStrong: TextStyle,
    val bodySm: TextStyle,
    val bodySmStrong: TextStyle,
    val caption: TextStyle,
    val button: TextStyle,
)

private fun display(size: Int, line: Int, weight: FontWeight = FontWeight.Black, tracking: Double = -0.5) =
    TextStyle(
        fontFamily = AgroFonts.Display,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )

private fun text(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) =
    TextStyle(
        fontFamily = AgroFonts.Text,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )

val DefaultAgroTypography = AgroTypography(
    displayMega = display(48, 52, tracking = -1.0),
    displayXxl = display(40, 44, tracking = -1.0),
    displayXl = display(32, 36),
    displayLg = display(28, 34, FontWeight.Normal, -0.1),
    displayMd = display(24, 28),
    displaySm = text(20, 26, FontWeight.SemiBold, -0.5),
    displayXs = text(18, 24, FontWeight.SemiBold, -0.2),
    bodyLg = text(18, 26),
    bodyMd = text(16, 24),
    bodyMdStrong = text(16, 24, FontWeight.SemiBold),
    bodySm = text(14, 20),
    bodySmStrong = text(14, 20, FontWeight.SemiBold),
    caption = text(12, 16),
    button = text(16, 24, FontWeight.SemiBold),
)

val LocalAgroTypography = staticCompositionLocalOf { DefaultAgroTypography }
