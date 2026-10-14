package org.nicho.arca.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The 8-step spacing scale (DSG-ARC-001 §5), plus names for the two values with a fixed job.
 * Whitespace replaced cards in this system: group with space and hairlines, not containers.
 */
@Immutable
data class ArcaSpacing(
    val space1: Dp,
    val space2: Dp,
    val space3: Dp,
    val space4: Dp,
    val space5: Dp,
    val space6: Dp,
    val space7: Dp,
    val space8: Dp,
) {
    /** Every screen's side margin: wider than the usual 16dp, so lists read unhurried (§5.1). */
    val screenMargin: Dp get() = space5

    /** Between sections: space alone, no divider or card (§5.1). */
    val sectionGap: Dp get() = space7
}

val ArcaSpacingTokens = ArcaSpacing(
    space1 = 4.dp,
    space2 = 8.dp,
    space3 = 12.dp,
    space4 = 16.dp,
    space5 = 24.dp,
    space6 = 32.dp,
    space7 = 48.dp,
    space8 = 72.dp,
)
