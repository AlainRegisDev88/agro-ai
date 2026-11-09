package com.agroai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

object AgroTheme {
    val colors: AgroColors
        @Composable @ReadOnlyComposable get() = LocalAgroColors.current
    val type: AgroTypography
        @Composable @ReadOnlyComposable get() = LocalAgroTypography.current
}

@Composable
fun AgroTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkAgroColors else LightAgroColors
    val type = DefaultAgroTypography

    // Material components (sheets, switches, snackbars, progress) pick up our roles too.
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryPale, onPrimaryContainer = colors.ink,
            secondary = colors.ink, onSecondary = colors.surface,
            background = colors.background, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.surfaceSoft, onSurfaceVariant = colors.body,
            surfaceContainerLow = colors.surface, surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface,
            outline = colors.mute, outlineVariant = colors.divider,
            error = colors.negative, onError = colors.surface,
        )
    } else {
        lightColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryPale, onPrimaryContainer = colors.ink,
            secondary = colors.ink, onSecondary = colors.surface,
            background = colors.background, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.surfaceSoft, onSurfaceVariant = colors.body,
            surfaceContainerLow = colors.surface, surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface,
            outline = colors.mute, outlineVariant = colors.divider,
            error = colors.negative, onError = colors.surface,
        )
    }
    val materialType = Typography(
        displayLarge = type.displayXxl, displayMedium = type.displayXl, displaySmall = type.displayMd,
        headlineLarge = type.displayXl, headlineMedium = type.displayMd, headlineSmall = type.displaySm,
        titleLarge = type.displaySm, titleMedium = type.bodyMdStrong, titleSmall = type.bodySmStrong,
        bodyLarge = type.bodyMd, bodyMedium = type.bodySm, bodySmall = type.caption,
        labelLarge = type.button, labelMedium = type.bodySmStrong, labelSmall = type.caption,
    )
    val shapes = Shapes(
        extraSmall = Radius.sm, small = Radius.md, medium = Radius.lg,
        large = Radius.xl, extraLarge = Radius.xl,
    )

    CompositionLocalProvider(LocalAgroColors provides colors, LocalAgroTypography provides type) {
        MaterialTheme(colorScheme = scheme, typography = materialType, shapes = shapes, content = content)
    }
}
