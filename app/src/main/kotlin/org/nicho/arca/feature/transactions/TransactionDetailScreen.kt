package org.nicho.arca.feature.transactions

import org.nicho.arca.ui.components.TransactionDetailSkeleton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.domain.model.TransactionId
import org.nicho.arca.domain.model.TransactionListItem
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.ui.components.DetailTopBar
import org.nicho.arca.ui.components.Hairline
import org.nicho.arca.ui.components.IconTile
import org.nicho.arca.ui.components.LockBadge
import org.nicho.arca.ui.components.MoneyFigure
import org.nicho.arca.ui.components.SectionLabel
import org.nicho.arca.ui.components.pressable
import org.nicho.arca.ui.components.pushArrival
import org.nicho.arca.ui.format.dateTime

@Composable
fun TransactionDetailRoute(
    onBack: () -> Unit,
    onEdit: (TransactionId) -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == TransactionDetailUiState.Gone) onBack() }
    TransactionDetailScreen(state, onBack, { onEdit(viewModel.transactionId) }, viewModel::onDelete)
}

/**
 * Transaction Detail (board 07): the kind and amount centred, then the facts on a sunken card.
 * A transfer shows From → To and its linked records, because deleting it deletes them all.
 */
@Composable
fun TransactionDetailScreen(
    state: TransactionDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = ArcaDesignSystem.spacing
    val colors = ArcaDesignSystem.colors
    val money = ArcaDesignSystem.money
    val ready = state as? TransactionDetailUiState.Ready
    val tx = ready?.primary?.transaction
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        DetailTopBar(
            onBack,
            actionLabel = stringResource(R.string.action_edit),
            // Adjustments are recorded from the account, not edited like a transaction.
            onAction = if (tx != null && !tx.type.isAdjustment) onEdit else null,
        )
        if (ready == null || tx == null) {
            TransactionDetailSkeleton()
            return@Column
        }
        val item = ready.primary
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .pushArrival()
                .padding(start = spacing.screenMargin, end = spacing.screenMargin, bottom = spacing.space5)
                .navigationBarsPadding(),
        ) {
            // Hero
            val (icon, tint) = when {
                ready.isTransfer -> ArcaIcons.transfer to colors.inkSecondary
                tx.type.isAdjustment -> ArcaIcons.adjustment to colors.inkSecondary
                tx.type == TransactionType.INCOME -> ArcaIcons.named(item.categoryIcon ?: "briefcase") to colors.income
                else -> ArcaIcons.named(item.categoryIcon ?: "tag") to (item.categoryColorHex?.let(colors::category) ?: colors.inkSecondary)
            }
            Column(Modifier.fillMaxWidth().padding(top = spacing.space5), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(52.dp).background(colors.surfaceSunken, ArcaDesignSystem.shapes.medium), contentAlignment = Alignment.Center) {
                    ArcaIcon(icon, tint = tint, size = 24.dp)
                }
                Text(
                    when {
                        ready.isTransfer -> stringResource(R.string.transaction_transfer)
                        tx.type.isAdjustment -> stringResource(R.string.transaction_adjustment)
                        else -> item.categoryName ?: stringResource(if (tx.type == TransactionType.INCOME) R.string.capture_mode_income else R.string.capture_mode_expense)
                    }.uppercase(),
                    style = ArcaDesignSystem.typography.label,
                    color = colors.inkSecondary,
                    modifier = Modifier.padding(top = spacing.space3),
                )
                val heroColor = when (tx.type) {
                    TransactionType.INCOME -> colors.income
                    else -> colors.ink
                }
                MoneyFigure(tx.amountMinor, color = heroColor, modifier = Modifier.padding(top = spacing.space3))
                Text(
                    dateTime(tx.date),
                    style = ArcaDesignSystem.typography.footnote,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(top = spacing.space3),
                )
            }

            if (ready.isTransfer) {
                // From → To
                Row(Modifier.padding(top = spacing.space6), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                    val out = ready.members.firstOrNull { it.transaction.type == TransactionType.TRANSFER_OUT } ?: item
                    FlowEnd(stringResource(R.string.capture_from), out.accountName, ready.accountIcons[out.transaction.accountId], Modifier.weight(1f))
                    ArcaIcon(ArcaIcons.arrowRight, tint = colors.inkTertiary, size = 18.dp)
                    FlowEnd(stringResource(R.string.capture_to), out.counterAccountName.orEmpty(), out.transaction.counterAccountId?.let { ready.accountIcons[it] }, Modifier.weight(1f))
                }
            }

            // Facts
            val fee = ready.members.firstOrNull { it.transaction.type == TransactionType.EXPENSE }
            val facts = buildList {
                if (ready.isTransfer) {
                    if (fee != null) add(Fact(stringResource(R.string.detail_transfer_fee), money.amount(fee.transaction.amountMinor), amount = true))
                } else {
                    if (!tx.type.isAdjustment) add(Fact(stringResource(R.string.capture_category), item.categoryName.orEmpty()))
                    add(Fact(stringResource(R.string.capture_account), item.accountName))
                }
                add(
                    Fact(
                        stringResource(R.string.detail_counted_in),
                        stringResource(if (tx.type in TransactionType.reportable && !ready.isTransfer) R.string.detail_reports_included else R.string.detail_reports_excluded),
                    ),
                )
                add(Fact(stringResource(R.string.capture_note), tx.note?.takeIf { it.isNotBlank() } ?: stringResource(R.string.detail_no_note), muted = tx.note.isNullOrBlank()))
            }
            Column(
                Modifier
                    .padding(top = spacing.space5)
                    .fillMaxWidth()
                    .background(colors.surfaceSunken, ArcaDesignSystem.shapes.medium)
                    .padding(horizontal = spacing.space4),
            ) {
                facts.forEachIndexed { i, fact ->
                    if (i > 0) Hairline()
                    Row(Modifier.padding(vertical = spacing.space4), verticalAlignment = Alignment.CenterVertically) {
                        Text(fact.label, style = ArcaDesignSystem.typography.subhead, color = colors.inkSecondary)
                        Text(
                            fact.value,
                            style = if (fact.amount) ArcaDesignSystem.typography.amountRow else ArcaDesignSystem.typography.subhead,
                            color = if (fact.muted) colors.inkTertiary else colors.ink,
                            textAlign = TextAlign.End,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = spacing.space4),
                        )
                    }
                }
            }

            if (ready.isTransfer) {
                Row(Modifier.padding(top = spacing.space6, bottom = spacing.space2), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(pluralStringResource(R.plurals.detail_linked_records, ready.members.size, ready.members.size), Modifier.weight(1f))
                    Text(ready.members.size.toString(), style = ArcaDesignSystem.typography.footnote, color = colors.inkTertiary)
                }
                Text(
                    pluralStringResource(R.plurals.detail_linked_delete_note, ready.members.size, ready.members.size),
                    style = ArcaDesignSystem.typography.footnote,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(bottom = spacing.space3),
                )
                ready.members.forEachIndexed { i, member ->
                    if (i > 0) Hairline(startInset = 48.dp)
                    LinkedRecord(member)
                }
            }

            // Delete, with the undo promise under it.
            Box(
                Modifier
                    .padding(top = spacing.space6)
                    .fillMaxWidth()
                    .pressable(onDelete, pressedScale = 0.98f)
                    .border(1.dp, colors.hairline, ArcaDesignSystem.shapes.medium)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (ready.isTransfer) R.string.detail_delete_transfer else R.string.detail_delete_transaction),
                    style = ArcaDesignSystem.typography.headline,
                    color = colors.negative,
                )
            }
            Text(
                stringResource(R.string.detail_undo_note),
                style = ArcaDesignSystem.typography.footnote,
                color = colors.inkTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.space3),
            )
        }
    }
}

private data class Fact(val label: String, val value: String, val amount: Boolean = false, val muted: Boolean = false)

/** One end of a transfer on a sunken block: "FROM / 🏛 BCA Utama". */
@Composable
private fun FlowEnd(label: String, name: String, icon: String?, modifier: Modifier = Modifier) {
    val colors = ArcaDesignSystem.colors
    Column(
        modifier
            .background(colors.surfaceSunken, ArcaDesignSystem.shapes.medium)
            .padding(ArcaDesignSystem.spacing.space4),
    ) {
        Text(label.uppercase(), style = ArcaDesignSystem.typography.label, color = colors.inkTertiary)
        Row(Modifier.padding(top = ArcaDesignSystem.spacing.space2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space2)) {
            ArcaIcon(ArcaIcons.named(icon ?: "wallet"), tint = colors.inkSecondary, size = 17.dp)
            Text(name, style = ArcaDesignSystem.typography.subhead, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A member of a transfer: Out, In, or the Fee with its lock. */
@Composable
private fun LinkedRecord(member: TransactionListItem) {
    val colors = ArcaDesignSystem.colors
    val tx = member.transaction
    val (icon, role) = when (tx.type) {
        TransactionType.TRANSFER_OUT -> ArcaIcons.arrowDown to stringResource(R.string.detail_leg_out)
        TransactionType.TRANSFER_IN -> ArcaIcons.arrowUp to stringResource(R.string.detail_leg_in)
        else -> ArcaIcons.banknote to stringResource(R.string.transaction_fee)
    }
    val isFee = tx.type == TransactionType.EXPENSE
    Row(
        Modifier.padding(vertical = ArcaDesignSystem.spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space3),
    ) {
        Box {
            IconTile(icon, if (isFee) colors.inkSecondary else colors.inkTertiary, size = 36.dp)
            if (isFee) LockBadge(Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(role, style = ArcaDesignSystem.typography.subhead, color = colors.ink)
            Text(
                if (isFee) stringResource(R.string.detail_fee_locked, member.accountName) else member.accountName,
                style = ArcaDesignSystem.typography.footnote,
                color = colors.inkTertiary,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Text(
            ArcaDesignSystem.money.transaction(tx.amountMinor, tx.type),
            style = ArcaDesignSystem.typography.amountRow,
            color = if (isFee) colors.ink else colors.inkTertiary,
        )
    }
}
