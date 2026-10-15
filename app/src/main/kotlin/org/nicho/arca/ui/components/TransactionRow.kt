package org.nicho.arca.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.domain.model.TransactionListItem
import org.nicho.arca.domain.model.TransactionType

/** What a row says about a transaction, in words. Shared by rows and detail screens. */
data class TransactionText(val title: String, val subtitle: String)

@Composable
fun transactionText(item: TransactionListItem, showAccount: Boolean = true): TransactionText {
    val tx = item.transaction
    val note = tx.note?.takeIf { it.isNotBlank() }
    return when {
        // Inside one account's history the account is a given: "To GoPay", "From BCA Utama".
        !showAccount && tx.type == TransactionType.TRANSFER_OUT ->
            TransactionText(stringResource(R.string.transaction_transfer), stringResource(R.string.transaction_to, item.counterAccountName ?: "?"))
        !showAccount && tx.type == TransactionType.TRANSFER_IN ->
            TransactionText(stringResource(R.string.transaction_transfer), stringResource(R.string.transaction_from, item.counterAccountName ?: "?"))
        tx.type == TransactionType.TRANSFER_OUT -> TransactionText(
            stringResource(R.string.transaction_transfer),
            item.accountName + " → " + (item.counterAccountName ?: "?"),
        )
        tx.type == TransactionType.TRANSFER_IN -> TransactionText(
            stringResource(R.string.transaction_transfer),
            (item.counterAccountName ?: "?") + " → " + item.accountName,
        )
        tx.type.isAdjustment -> TransactionText(
            stringResource(R.string.transaction_adjustment),
            listOfNotNull(item.accountName.takeIf { showAccount }, note).joinToString(" · "),
        )
        tx.transferGroupId != null -> TransactionText( // a transfer's fee
            item.categoryName ?: stringResource(R.string.transaction_fee),
            stringResource(R.string.transaction_fee_subtitle, item.accountName),
        )
        else -> TransactionText(
            item.categoryName ?: "",
            listOfNotNull(item.accountName.takeIf { showAccount }, note).joinToString(" · "),
        )
    }
}

/**
 * A ledger row (DSG-ARC-001 §3.2, §7.2): a 40dp tile, the category or "Transfer", a secondary
 * line, and the amount. Expense amounts are ink with "−", income is the income hue with "+",
 * a transfer leg is ink-tertiary and unsigned. Only an expense tile takes the category hue.
 */
@Composable
fun TransactionRow(
    item: TransactionListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showAccount: Boolean = true,
    trailingCaption: String? = null,
) {
    val colors = ArcaDesignSystem.colors
    val tx = item.transaction
    val text = transactionText(item, showAccount)
    val isFee = tx.type == TransactionType.EXPENSE && tx.transferGroupId != null
    val (icon, tint) = when (tx.type) {
        TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN -> ArcaIcons.transfer to colors.inkTertiary
        TransactionType.ADJUSTMENT_IN, TransactionType.ADJUSTMENT_OUT -> ArcaIcons.adjustment to colors.inkTertiary
        TransactionType.INCOME -> ArcaIcons.named(item.categoryIcon ?: "briefcase") to colors.income
        TransactionType.EXPENSE -> ArcaIcons.named(item.categoryIcon ?: "tag") to
            (if (isFee) colors.inkSecondary else item.categoryColorHex?.let(colors::category) ?: colors.inkSecondary)
    }
    val amountColor = when (tx.type) {
        TransactionType.INCOME -> colors.income
        TransactionType.EXPENSE -> colors.ink
        TransactionType.ADJUSTMENT_IN, TransactionType.ADJUSTMENT_OUT -> colors.inkSecondary
        else -> colors.inkTertiary
    }
    val amount = ArcaDesignSystem.money.transaction(tx.amountMinor, tx.type)
    val spokenAmount = ArcaDesignSystem.spokenMoney.format(tx.amountMinor)
    Row(
        modifier
            .fillMaxWidth()
            .pressable(onClick, pressedScale = 1f)
            .clearAndSetSemantics { contentDescription = "${text.title}, ${text.subtitle}, $spokenAmount" }
            .padding(vertical = ArcaDesignSystem.spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            IconTile(icon, tint)
            if (isFee) LockBadge(Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = ArcaDesignSystem.spacing.space3),
        ) {
            Text(text.title, style = ArcaDesignSystem.typography.headline, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (text.subtitle.isNotEmpty()) {
                Text(
                    text.subtitle,
                    style = ArcaDesignSystem.typography.footnote,
                    color = colors.inkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(amount, style = ArcaDesignSystem.typography.amountRow, color = amountColor)
            if (trailingCaption != null) {
                Text(trailingCaption, style = ArcaDesignSystem.typography.footnote, color = colors.inkTertiary)
            }
        }
    }
}

/** The small badge on a fee's tile: Fees can't be deleted or renamed away (DSG-ARC-001 §7.4). */
@Composable
fun LockBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(16.dp)
            .clip(ArcaDesignSystem.shapes.pill)
            .background(ArcaDesignSystem.colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        ArcaIcon(ArcaIcons.lock, tint = ArcaDesignSystem.colors.inkSecondary, size = 10.dp)
    }
}

/**
 * A day header in a list: "YESTERDAY ………… −542,500". A day that came out ahead shows its net in
 * the income hue, as the prototype does; any other day stays tertiary. [first] is the list's
 * first group, which sits closer to the chips above it.
 */
@Composable
fun DayHeader(label: String, netMinor: Long?, modifier: Modifier = Modifier, first: Boolean = false) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = if (first) ArcaDesignSystem.spacing.space5 else ArcaDesignSystem.spacing.space6, bottom = ArcaDesignSystem.spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(label, Modifier.weight(1f))
        if (netMinor != null) {
            Text(
                ArcaDesignSystem.money.signed(netMinor, withCurrency = false),
                style = ArcaDesignSystem.typography.footnote,
                color = if (netMinor > 0) ArcaDesignSystem.colors.income else ArcaDesignSystem.colors.inkTertiary,
            )
        }
    }
}
