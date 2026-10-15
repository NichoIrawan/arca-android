package org.nicho.arca.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.domain.calculation.EnvelopeCalculator
import org.nicho.arca.domain.model.Envelope

/**
 * One envelope on the Budget tab (DSG-ARC-001 §10.10, FR-264), as the prototype draws it: the
 * category tile, the amount *left* as the primary figure, a bar that drains 100% → 0, and a
 * "Spent X of Y" line that never disappears. [footnote] follows it on the same line (the reset
 * date); [rollLine] is a second line with the roll-over glyph.
 *
 * Overspending (client decision, 2026-10-14) is a warning, not a judgment: the "Over by" pill and
 * the empty bar's track take the `negative` colour so it can't be missed.
 */
@Composable
fun EnvelopeRow(
    name: String,
    envelope: Envelope,
    hue: Color,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leftWord: String = stringResource(R.string.budget_left),
    footnote: String? = null,
    rollLine: String? = null,
    index: Int = 0,
    replayKey: Any? = Unit,
) {
    val colors = ArcaDesignSystem.colors
    val money = ArcaDesignSystem.money
    val spacing = ArcaDesignSystem.spacing
    val over = envelope.leftMinor < 0
    Row(
        modifier
            .fillMaxWidth()
            .pressable(onClick, pressedScale = 1f)
            .padding(vertical = spacing.space3),
        horizontalArrangement = Arrangement.spacedBy(spacing.space3),
    ) {
        IconTile(icon, hue)
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = ArcaDesignSystem.typography.headline, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (over) {
                    OverPill(money.amount(EnvelopeCalculator.overBy(envelope.availableMinor, envelope.spentMinor)), Modifier.padding(start = spacing.space2))
                } else {
                    LeftFigure(money.amount(envelope.leftMinor), leftWord, Modifier.padding(start = spacing.space2))
                }
            }
            ProgressBar(
                fraction = EnvelopeCalculator.leftFraction(envelope.availableMinor, envelope.leftMinor),
                color = hue,
                track = if (over) colors.negative.copy(alpha = 0.16f) else colors.surfaceSunken,
                modifier = Modifier.padding(top = spacing.space2),
                delayMillis = 200 + index * ArcaDesignSystem.motion.revealStepMillis,
                replayKey = replayKey,
            )
            val spent = stringResource(R.string.budget_spent_of, money.amount(envelope.spentMinor), money.amount(envelope.availableMinor))
            Text(
                if (footnote != null) "$spent · $footnote" else spent,
                style = ArcaDesignSystem.typography.footnote,
                color = colors.inkTertiary,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (rollLine != null) {
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ArcaIcon(ArcaIcons.repeat, tint = colors.inkSecondary, size = 13.dp)
                    Text(rollLine, style = ArcaDesignSystem.typography.footnote, color = colors.inkTertiary)
                }
            }
        }
    }
}

/**
 * The Overview's compact envelope: name, "Rp35K left" or the "Over by" pill, the bar, and "Spent
 * Rp965K of Rp1M". No tile; amounts are compact.
 */
@Composable
fun CompactEnvelopeRow(
    name: String,
    envelope: Envelope,
    hue: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ArcaDesignSystem.colors
    val money = ArcaDesignSystem.money
    val over = envelope.leftMinor < 0
    Column(
        modifier
            .fillMaxWidth()
            .pressable(onClick, pressedScale = 1f)
            .padding(vertical = ArcaDesignSystem.spacing.space2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = ArcaDesignSystem.typography.subhead, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (over) {
                OverPill(money.compact(EnvelopeCalculator.overBy(envelope.availableMinor, envelope.spentMinor)), Modifier.padding(start = ArcaDesignSystem.spacing.space2))
            } else {
                LeftFigure(money.compact(envelope.leftMinor), stringResource(R.string.budget_left), Modifier.padding(start = ArcaDesignSystem.spacing.space2))
            }
        }
        ProgressBar(
            fraction = EnvelopeCalculator.leftFraction(envelope.availableMinor, envelope.leftMinor),
            color = hue,
            track = if (over) colors.negative.copy(alpha = 0.16f) else colors.surfaceSunken,
            modifier = Modifier.padding(top = 7.dp),
            delayMillis = 260,
        )
        Text(
            stringResource(R.string.budget_spent_of, money.compact(envelope.spentMinor), money.compact(envelope.availableMinor)),
            style = ArcaDesignSystem.typography.footnote,
            color = colors.inkTertiary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** "Rp250,000 left": the figure, then the word, baseline to baseline. */
@Composable
private fun LeftFigure(amount: String, word: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(amount, style = ArcaDesignSystem.typography.amountRow, color = ArcaDesignSystem.colors.ink, maxLines = 1)
        Text(word, style = ArcaDesignSystem.typography.footnote, color = ArcaDesignSystem.colors.inkTertiary, maxLines = 1, modifier = Modifier.padding(bottom = 1.dp))
    }
}

/**
 * "Over by Rp40K". The prototype draws it in ink on a sunken fill; at the client's request
 * (2026-10-14) it carries the `negative` colour instead: a warning that the envelope is spent,
 * still worded without blame.
 */
@Composable
fun OverPill(amount: String, modifier: Modifier = Modifier) {
    val colors = ArcaDesignSystem.colors
    Text(
        stringResource(R.string.budget_over_by, amount),
        style = ArcaDesignSystem.typography.footnote,
        color = colors.negative,
        maxLines = 1,
        modifier = modifier
            .background(colors.negative.copy(alpha = 0.12f), ArcaDesignSystem.shapes.pill)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
