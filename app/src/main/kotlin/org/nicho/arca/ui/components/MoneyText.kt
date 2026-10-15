package org.nicho.arca.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.domain.formatting.MoneyFormat

/**
 * A large figure with its "Rp" mark set small beside it (DSG-ARC-001 §4.2): the mark recedes by
 * size, not by fading. TalkBack hears the amount once, as words (NFR-A01).
 *
 * [magnitude] drops the sign, for a figure whose label already says the direction ("Over by").
 * [rolling] makes the digits roll when the value changes in place (the prototype's NumberFlow).
 */
@Composable
fun MoneyFigure(
    amountMinor: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = ArcaDesignSystem.typography.amountHero,
    color: Color = ArcaDesignSystem.colors.ink,
    markAlpha: Float = 1f,
    markColor: Color = if (markAlpha < 1f) color.copy(alpha = markAlpha) else ArcaDesignSystem.colors.inkTertiary,
    magnitude: Boolean = false,
    rolling: Boolean = false,
    suffix: (@Composable () -> Unit)? = null,
) {
    val spoken = ArcaDesignSystem.spokenMoney.format(amountMinor)
    val money = ArcaDesignSystem.money
    val digits = if (magnitude) money.magnitude(amountMinor) else money.digits(amountMinor)
    Row(
        modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            MoneyFormat.CURRENCY,
            style = ArcaDesignSystem.typography.currencyMark,
            color = markColor,
            modifier = Modifier.padding(end = 4.dp, bottom = 6.dp),
        )
        if (rolling) RollingText(digits, style, color) else Text(digits, style = style, color = color)
        suffix?.invoke()
    }
}
