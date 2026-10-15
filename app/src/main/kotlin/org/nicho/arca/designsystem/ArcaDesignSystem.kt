package org.nicho.arca.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import org.nicho.arca.domain.formatting.AccessibleMoneyFormat
import org.nicho.arca.domain.formatting.MoneyFormat

// Provided by ArcaTheme. Reading one outside ArcaTheme fails loudly instead of silently
// falling back to light colours on a dark screen.
internal val LocalArcaColors = staticCompositionLocalOf<ArcaColors> { missingTheme() }
internal val LocalArcaTypography = staticCompositionLocalOf<ArcaTypography> { missingTheme() }
internal val LocalArcaSpacing = staticCompositionLocalOf<ArcaSpacing> { missingTheme() }
internal val LocalArcaShapes = staticCompositionLocalOf<ArcaShapes> { missingTheme() }
internal val LocalArcaShadow = staticCompositionLocalOf<ArcaShadow> { missingTheme() }
internal val LocalArcaMotion = staticCompositionLocalOf<ArcaMotion> { missingTheme() }
internal val LocalMoneyFormat = staticCompositionLocalOf<MoneyFormat> { missingTheme() }
internal val LocalSpokenMoney = staticCompositionLocalOf<AccessibleMoneyFormat> { missingTheme() }

private fun missingTheme(): Nothing = error("No Arca design tokens here: wrap this UI in ArcaTheme { … }")

/**
 * How UI reads the design tokens. Never hard-code a colour, size or text style.
 *
 *     Text(
 *         text = ArcaDesignSystem.money.transaction(amount, TransactionType.INCOME),
 *         color = ArcaDesignSystem.colors.income,
 *         style = ArcaDesignSystem.typography.amountRow,
 *         modifier = Modifier.padding(horizontal = ArcaDesignSystem.spacing.screenMargin),
 *     )
 */
object ArcaDesignSystem {
    val colors: ArcaColors
        @Composable @ReadOnlyComposable get() = LocalArcaColors.current

    val typography: ArcaTypography
        @Composable @ReadOnlyComposable get() = LocalArcaTypography.current

    val spacing: ArcaSpacing
        @Composable @ReadOnlyComposable get() = LocalArcaSpacing.current

    val shapes: ArcaShapes
        @Composable @ReadOnlyComposable get() = LocalArcaShapes.current

    val shadow: ArcaShadow
        @Composable @ReadOnlyComposable get() = LocalArcaShadow.current

    val motion: ArcaMotion
        @Composable @ReadOnlyComposable get() = LocalArcaMotion.current

    /** Number format for the language the app is showing: "Rp24,850,000" or "Rp24.850.000". */
    val money: MoneyFormat
        @Composable @ReadOnlyComposable get() = LocalMoneyFormat.current

    /** Amounts as words for TalkBack, in the app's language (NFR-A01). */
    val spokenMoney: AccessibleMoneyFormat
        @Composable @ReadOnlyComposable get() = LocalSpokenMoney.current
}
