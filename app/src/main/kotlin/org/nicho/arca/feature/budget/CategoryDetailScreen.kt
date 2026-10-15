package org.nicho.arca.feature.budget

import org.nicho.arca.ui.components.DetailSkeleton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.YearMonth
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.designsystem.Mascot
import org.nicho.arca.designsystem.MascotMood
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.model.TransactionId
import org.nicho.arca.domain.model.TransactionListItem
import org.nicho.arca.domain.readmodel.CategoryDetailModel
import org.nicho.arca.domain.readmodel.SortOrder
import org.nicho.arca.ui.components.DetailTopBar
import org.nicho.arca.ui.components.Hairline
import org.nicho.arca.ui.components.IconTile
import org.nicho.arca.ui.components.MoneyFigure
import org.nicho.arca.ui.components.ProgressBar
import org.nicho.arca.ui.components.RollingText
import org.nicho.arca.ui.components.SectionLabel
import org.nicho.arca.ui.components.SegmentedControl
import org.nicho.arca.ui.components.TextChip
import org.nicho.arca.ui.components.pressable
import org.nicho.arca.ui.components.pushArrival
import org.nicho.arca.ui.format.dayLabel
import org.nicho.arca.ui.format.monthLabel

@Composable
fun CategoryDetailRoute(
    onBack: () -> Unit,
    onEdit: (CategoryId) -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
    viewModel: CategoryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == CategoryDetailUiState.Gone) onBack() }
    CategoryDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = { onEdit(viewModel.categoryId) },
        onOpenTransaction = onOpenTransaction,
        onMonthChange = viewModel::onMonthChange,
        onAccountFilter = viewModel::onAccountFilter,
        onSortChange = viewModel::onSortChange,
    )
}

/**
 * Category Detail (board 25, FR-264, FR-268): the envelope — what's left, the drain bar, and
 * Budget · Spent · Carried in — then every transaction of the month under a filter row that stays
 * pinned while the list scrolls. The mascot reads this one envelope.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryDetailScreen(
    state: CategoryDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onAccountFilter: (AccountId?) -> Unit,
    onSortChange: (SortOrder) -> Unit,
) {
    val spacing = ArcaDesignSystem.spacing
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        DetailTopBar(onBack, actionLabel = stringResource(R.string.action_edit), onAction = onEdit)
        val ready = state as? CategoryDetailUiState.Ready
        if (ready == null) {
            DetailSkeleton(chart = false)
            return@Column
        }
        val model = ready.model
        val accountIcons = ready.filterAccounts.associate { it.id to it.icon }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .pushArrival(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = spacing.space5),
        ) {
            item { Hero(model, Modifier.padding(start = spacing.screenMargin, end = spacing.screenMargin, bottom = spacing.space6)) }
            stickyHeader { FilterBar(ready, onAccountFilter, onSortChange) }
            model.days.forEachIndexed { dayIndex, day ->
                if (day.date != null) {
                    item(key = "day-${day.date}") {
                        Row(
                            Modifier
                                .padding(horizontal = spacing.screenMargin)
                                .padding(top = if (dayIndex == 0) spacing.space4 else spacing.space6, bottom = spacing.space3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionLabel(dayLabel(day.date!!), Modifier.weight(1f))
                            day.netMinor?.let {
                                Text(ArcaDesignSystem.money.signed(it, withCurrency = false), style = ArcaDesignSystem.typography.footnote, color = ArcaDesignSystem.colors.inkTertiary)
                            }
                        }
                    }
                }
                itemsIndexed(day.items, key = { _, item -> item.transaction.id.value }) { i, item ->
                    Column(Modifier.padding(horizontal = spacing.screenMargin).padding(top = if (day.date == null && i == 0) spacing.space2 else 0.dp)) {
                        if (i > 0) Hairline(startInset = 52.dp)
                        CategoryTransactionRow(item, model, accountIcons, showDate = ready.sort == SortOrder.LARGEST, onClick = { onOpenTransaction(item.transaction.id) })
                    }
                }
            }
            item { Box(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun Hero(model: CategoryDetailModel, modifier: Modifier = Modifier) {
    val colors = ArcaDesignSystem.colors
    val spacing = ArcaDesignSystem.spacing
    val money = ArcaDesignSystem.money
    val category = model.category
    val envelope = model.envelope
    val hue = colors.category(category.colorHex)
    Column(modifier.padding(top = spacing.space5)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
            IconTile(ArcaIcons.named(category.icon), if (category.kind == CategoryKind.INCOME) colors.income else hue, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(category.name, style = ArcaDesignSystem.typography.headline, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(if (category.kind == CategoryKind.INCOME) R.string.category_kind_income else R.string.category_kind_expense) +
                        if (category.kind == CategoryKind.EXPENSE) " · " + stringResource(if (category.rollsOver) R.string.category_rolls_over else R.string.category_resets_monthly) else "",
                    style = ArcaDesignSystem.typography.footnote,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Text(
                monthLabel(model.month),
                style = ArcaDesignSystem.typography.footnote,
                color = colors.inkSecondary,
                modifier = Modifier
                    .background(colors.surfaceSunken, ArcaDesignSystem.shapes.pill)
                    .padding(horizontal = spacing.space3, vertical = 5.dp),
            )
        }
        if (envelope != null) {
            val over = envelope.leftMinor < 0
            Row(Modifier.padding(top = spacing.space5), verticalAlignment = Alignment.Bottom) {
                MoneyFigure(
                    envelope.leftMinor,
                    color = if (over) colors.negative else colors.ink,
                    magnitude = true,
                    rolling = true,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(if (over) R.string.category_over else R.string.budget_left),
                        style = ArcaDesignSystem.typography.headline.copy(fontWeight = FontWeight.Normal),
                        color = colors.inkSecondary,
                        modifier = Modifier.padding(start = spacing.space2, bottom = 8.dp),
                    )
                }
                Mascot(MascotMood.of(envelope.status), size = 40.dp)
            }
            ProgressBar(
                model.leftFraction,
                hue,
                Modifier.padding(top = spacing.space4),
                track = if (over) colors.negative.copy(alpha = 0.16f) else colors.surfaceSunken,
                delayMillis = 160,
                replayKey = model.month,
            )
            Row(Modifier.padding(top = spacing.space2), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.category_percent_left, (model.leftFraction * 100).toInt()),
                    style = ArcaDesignSystem.typography.footnote,
                    color = if (over) colors.negative else colors.inkSecondary,
                    modifier = Modifier.weight(1f),
                )
                val (resetLine, rollLine) = envelopeLines(envelope, model.month, model.monthState)
                Text((resetLine ?: rollLine).orEmpty(), style = ArcaDesignSystem.typography.footnote, color = colors.inkTertiary)
            }
            Row(Modifier.padding(top = spacing.space5), horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
                Stat(stringResource(R.string.category_budget), money.digits(envelope.budgetMinor), Modifier.weight(1f))
                Stat(stringResource(R.string.category_spent), money.digits(envelope.spentMinor), Modifier.weight(1f))
                Stat(stringResource(R.string.category_carried_in), money.digits(envelope.carryMinor), Modifier.weight(1f))
            }
        } else {
            // No envelope: what was spent (or received) this month, and why there's no bar.
            MoneyFigure(model.shownTotalMinor.takeIf { model.count > 0 } ?: (model.notBudgeted?.totalMinor ?: 0L), modifier = Modifier.padding(top = spacing.space5)) {
                Text(
                    stringResource(if (category.kind == CategoryKind.INCOME) R.string.category_received_word else R.string.category_spent_word),
                    style = ArcaDesignSystem.typography.headline.copy(fontWeight = FontWeight.Normal),
                    color = colors.inkSecondary,
                    modifier = Modifier.padding(start = spacing.space2, bottom = 8.dp),
                )
            }
            Text(
                stringResource(
                    when {
                        category.kind == CategoryKind.INCOME -> R.string.category_income_no_budget
                        category.isSystemLocked -> R.string.category_never_budgeted
                        else -> R.string.category_not_budgeted_help
                    },
                ),
                style = ArcaDesignSystem.typography.footnote,
                color = colors.inkTertiary,
                modifier = Modifier.padding(top = spacing.space2),
            )
        }
    }
}

/** A sunken stat block: "Budget / 2,500,000". */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(ArcaDesignSystem.colors.surfaceSunken, ArcaDesignSystem.shapes.medium)
            .padding(horizontal = ArcaDesignSystem.spacing.space3, vertical = ArcaDesignSystem.spacing.space4),
    ) {
        Text(label, style = ArcaDesignSystem.typography.footnote, color = ArcaDesignSystem.colors.inkSecondary, maxLines = 1)
        Text(value, style = ArcaDesignSystem.typography.amountRow, color = ArcaDesignSystem.colors.ink, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * The sticky filter: account chips (single select), then "18 transactions · Rp2,250,000" and
 * Newest / Largest. It pins under the header while the list scrolls, on the page colour with a
 * rule under it.
 */
@Composable
private fun FilterBar(ready: CategoryDetailUiState.Ready, onAccountFilter: (AccountId?) -> Unit, onSortChange: (SortOrder) -> Unit) {
    val colors = ArcaDesignSystem.colors
    val spacing = ArcaDesignSystem.spacing
    val model = ready.model
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(top = spacing.space3),
    ) {
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = spacing.screenMargin),
            horizontalArrangement = Arrangement.spacedBy(spacing.space2),
        ) {
            item { TextChip(stringResource(R.string.filter_all), ready.selectedAccount == null, { onAccountFilter(null) }, Modifier.padding(vertical = 6.dp)) }
            items(ready.filterAccounts, key = { it.id.value }) { account ->
                TextChip(account.name, ready.selectedAccount == account.id, { onAccountFilter(account.id) }, Modifier.padding(vertical = 6.dp))
            }
        }
        Row(
            Modifier
                .padding(horizontal = spacing.screenMargin)
                .padding(top = spacing.space1, bottom = spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val count = pluralStringResource(R.plurals.category_transaction_count, model.count, model.count)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text("$count · ", style = ArcaDesignSystem.typography.footnote, color = colors.inkSecondary, maxLines = 1)
                Text("Rp", style = ArcaDesignSystem.typography.footnote.copy(fontWeight = FontWeight.Medium), color = colors.ink)
                RollingText(ArcaDesignSystem.money.digits(model.shownTotalMinor), ArcaDesignSystem.typography.footnote.copy(fontWeight = FontWeight.Medium), colors.ink)
            }
            SegmentedControl(
                options = listOf(SortOrder.NEWEST to stringResource(R.string.sort_newest), SortOrder.LARGEST to stringResource(R.string.sort_largest)),
                selected = ready.sort,
                onSelect = onSortChange,
                compact = true,
                modifier = Modifier.width(152.dp),
            )
        }
        Hairline()
    }
}

/**
 * A row in Category Detail: the account's tile, the note as the title (the category is the
 * screen's own), the account under it. Sorted by size, the date moves under the amount.
 */
@Composable
private fun CategoryTransactionRow(item: TransactionListItem, model: CategoryDetailModel, accountIcons: Map<AccountId, String>, showDate: Boolean, onClick: () -> Unit) {
    val colors = ArcaDesignSystem.colors
    val tx = item.transaction
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick, pressedScale = 1f)
            .padding(vertical = ArcaDesignSystem.spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space3),
    ) {
        IconTile(ArcaIcons.named(accountIcons[tx.accountId] ?: "wallet"), colors.inkSecondary)
        Column(Modifier.weight(1f)) {
            Text(
                tx.note?.takeIf { it.isNotBlank() } ?: model.category.name,
                style = ArcaDesignSystem.typography.headline,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(item.accountName, style = ArcaDesignSystem.typography.footnote, color = colors.inkTertiary, modifier = Modifier.padding(top = 1.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                ArcaDesignSystem.money.transaction(tx.amountMinor, tx.type),
                style = ArcaDesignSystem.typography.amountRow,
                color = if (model.category.kind == CategoryKind.INCOME) colors.income else colors.ink,
            )
            if (showDate) {
                Text(
                    dayLabel(tx.date.atZone(java.time.ZoneId.systemDefault()).toLocalDate()),
                    style = ArcaDesignSystem.typography.footnote,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}
