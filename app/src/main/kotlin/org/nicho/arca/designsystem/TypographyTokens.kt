package org.nicho.arca.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The type scale (DSG-ARC-001 §4), from the Android prototype: one family, Roboto, the platform
 * default. Six text styles and four amount styles.
 *
 * The number is the loudest thing on every screen: the hero figure is 48sp at weight 300, and
 * titles are smaller than Material's own. Every amount style uses tabular figures, so a changing
 * total doesn't jiggle as its digits change (§4.3).
 *
 * Sizes are in sp, so they follow the system font scale (NFR-A02).
 */
@Immutable
data class ArcaTypography(
    val title: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val subhead: TextStyle,
    val footnote: TextStyle,
    /** Section labels. Uppercase the text yourself, e.g. `stringResource(…).uppercase()`: Compose has no text-transform. */
    val label: TextStyle,
    val amountHero: TextStyle,     // net position
    val amountTitle: TextStyle,
    val amountRow: TextStyle,      // list rows
    val currencyMark: TextStyle,   // the "Rp" beside a large figure: smaller, not paler (§3.7)
)

// CSS line-height centres the text in its line box; Compose's default doesn't. This matches the
// prototype's vertical rhythm.
private val CssLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    weight: Int,
    size: Int,
    lineHeight: Int,
    letterSpacingEm: Float = 0f,
    tabularFigures: Boolean = false,
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacingEm.em,
    fontFeatureSettings = if (tabularFigures) "tnum" else null,
    lineHeightStyle = CssLineHeight,
)

val ArcaTypographyTokens = ArcaTypography(
    title = style(600, 26, 32, letterSpacingEm = -0.02f),
    headline = style(500, 16, 21),
    body = style(400, 16, 22),
    subhead = style(400, 14, 19),
    footnote = style(400, 13, 18),
    label = style(500, 11, 14, letterSpacingEm = 0.08f),
    amountHero = style(300, 48, 52, letterSpacingEm = -0.03f, tabularFigures = true),
    amountTitle = style(400, 28, 32, letterSpacingEm = -0.02f, tabularFigures = true),
    amountRow = style(500, 16, 21, letterSpacingEm = -0.01f, tabularFigures = true),
    currencyMark = style(400, 13, 18, tabularFigures = true),
)
