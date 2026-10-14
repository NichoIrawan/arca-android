package org.nicho.arca.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.nicho.arca.domain.repository.ThemePreference

/**
 * The root of every Arca screen: MainActivity, CaptureActivity and every @Preview.
 *
 * Provides the Arca tokens (read them through ArcaDesignSystem) and maps them onto Material 3,
 * so stock components (NavigationBar, Snackbar, TextField, Switch…) use Arca colours, type and
 * shapes without per-call styling.
 *
 * The colour scheme is fixed. No dynamic, wallpaper-derived colour: it would repaint the
 * category hues that colour-blind users rely on alongside icons (SDD-ARC-002 §9.9).
 *
 * [theme] is the user's choice from Settings (FR-904); SYSTEM follows the device.
 */
@Composable
fun ArcaTheme(
    theme: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (theme) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val colors = if (dark) ArcaDarkColors else ArcaLightColors

    CompositionLocalProvider(
        LocalArcaColors provides colors,
        LocalArcaTypography provides ArcaTypographyTokens,
        LocalArcaSpacing provides ArcaSpacingTokens,
        LocalArcaShapes provides ArcaShapeTokens,
        LocalArcaShadow provides if (dark) ArcaDarkShadow else ArcaLightShadow,
        LocalArcaMotion provides ArcaMotionTokens,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = ArcaTypographyTokens.toMaterialTypography(),
            shapes = ArcaShapeTokens.toMaterialShapes(),
            content = content,
        )
    }
}

// ── Mapping onto Material 3 ──
// Material has more roles than Arca has tokens, so several roles share one. The rules: the one
// accent is Material's primary; neutral surfaces and ink fill the surface roles; error is
// `negative`. `brand`, `warning` and the category hues have no Material role on purpose: only
// Arca components may use them.

private fun ArcaColors.toMaterialColorScheme(): ColorScheme {
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = accent,
        onPrimary = accentOn,
        primaryContainer = surfaceSunken,
        onPrimaryContainer = ink,
        inversePrimary = surface,
        secondary = inkSecondary,
        onSecondary = surface,
        secondaryContainer = surfaceSunken, // e.g. the selected NavigationBar indicator
        onSecondaryContainer = ink,
        tertiary = inkSecondary,
        onTertiary = surface,
        tertiaryContainer = surfaceSunken,
        onTertiaryContainer = ink,
        background = surface,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceSunken,
        onSurfaceVariant = inkSecondary,
        surfaceTint = surface,              // no tinted elevation: surfaces stay neutral
        inverseSurface = ink,
        inverseOnSurface = surface,
        error = negative,
        onError = surface,
        errorContainer = surfaceSunken,
        onErrorContainer = negative,
        outline = inkTertiary,
        outlineVariant = hairline,
        scrim = surfaceHero,
        surfaceBright = surfaceRaised,
        surfaceDim = surfaceSunken,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surfaceRaised,   // NavigationBar, menus
        surfaceContainerHigh = surfaceRaised, // bottom sheets, dialogs
        surfaceContainerHighest = surfaceSunken, // filled text fields
    )
}

private fun ArcaTypography.toMaterialTypography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = amountHero,
        displayMedium = amountHero,
        displaySmall = amountTitle,
        headlineLarge = amountTitle,
        headlineMedium = title,
        headlineSmall = title,
        titleLarge = title,
        titleMedium = headline,
        titleSmall = subhead,
        bodyLarge = body,
        bodyMedium = subhead,
        bodySmall = footnote,
        labelLarge = headline,   // buttons
        labelMedium = footnote,  // navigation bar labels
        labelSmall = label,
    )
}

private fun ArcaShapes.toMaterialShapes() = Shapes(
    extraSmall = small,
    small = small,
    medium = medium,
    large = large,
    extraLarge = large,
)
