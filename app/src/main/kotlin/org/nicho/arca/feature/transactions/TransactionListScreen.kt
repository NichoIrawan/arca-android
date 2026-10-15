package org.nicho.arca.feature.transactions

import org.nicho.arca.ui.components.ListSkeleton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.YearMonth
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionId
import org.nicho.arca.domain.model.TransactionListItem
import org.nicho.arca.domain.readmodel.PeriodChoice
import org.nicho.arca.domain.readmodel.TransactionDay
import org.nicho.arca.domain.readmodel.TransactionFilter
import org.nicho.arca.domain.readmodel.TypeFilter
import org.nicho.arca.ui.components.DayHeader
import org.nicho.arca.ui.components.EmptyState
import org.nicho.arca.ui.components.Hairline
import org.nicho.arca.ui.components.IconAction
import org.nicho.arca.ui.components.ScreenHeader
import org.nicho.arca.ui.components.TextChip
import org.nicho.arca.ui.components.TransactionRow
import org.nicho.arca.ui.components.pressable
import org.nicho.arca.ui.format.dayLabel

@Composable
fun TransactionListRoute(
    onOpenTransaction: (TransactionId) -> Unit,
    onOpenSearch: () -> Unit,
    onAddTransaction: () -> Unit,
    viewModel: TransactionListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draftCount by viewModel.draftCount.collectAsStateWithLifecycle()
    TransactionListScreen(
        state = state,
        draftCount = draftCount,
        onOpenTransaction = onOpenTransaction,
        onOpenSearch = onOpenSearch,
        onApplyFilter = viewModel::onApplyFilter,
        onDraftChange = viewModel::onDraftChange,
        onResetFilter = viewModel::onResetFilter,
        onDelete = viewModel::onDelete,
    )
}

/**
 * The Transactions tab (boards 01, 13, 17, 28): the month's rows by day. With no filter the chips
 * show what's in view (the period always on); with one, each active filter is a chip with its own
 * ✕ and "Clear all" sits beside them, and the filter button carries a dot.
 */
@Composable
fun TransactionListScreen(
    state: TransactionListUiState,
    draftCount: Int?,
    onOpenTransaction: (TransactionId) -> Unit,
    onOpenSearch: () -> Unit,
    onApplyFilter: (TransactionFilter) -> Unit,
    onDraftChange: (TransactionFilter) -> Unit,
    onResetFilter: () -> Unit,
    onDelete: (Transaction) -> Unit,
) {
    val spacing = ArcaDesignSystem.spacing
    val colors = ArcaDesignSystem.colors
    val money = ArcaDesignSystem.money
    var filtering by rememberSaveable { mutableStateOf(false) }
    val ready = state as? TransactionListUiState.Ready
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        if (ready == null || ready.model.ledgerIsEmpty) {
            // Quiet: no search, filter or chips while there is nothing to search (board 17).
            ScreenHeader(stringResource(R.string.nav_transactions))
            if (ready != null) {
                EmptyState(
                    title = stringResource(R.string.transactions_empty_title),
                    body = stringResource(R.string.transactions_empty_body),
                )
            } else {
                ListSkeleton(Modifier.padding(top = spacing.space2))
            }
            return@Column
        }
        val filter = ready.filter
        val narrowed = filter.type != TypeFilter.ALL || filter.accountIds.isNotEmpty() || filter.categoryIds.isNotEmpty()
        val thisMonth = filter.period == PeriodChoice.Month(YearMonth.now())
        ScreenHeader(
            title = stringResource(R.string.nav_transactions),
            subtitle = if (narrowed) {
                pluralStringResource(R.plurals.transactions_results, ready.model.count, ready.model.count) + " · " +
                    stringResource(R.string.transactions_out_plain, money.amount(ready.model.expenseMinor))
            } else {
                stringResource(if (thisMonth) R.string.transactions_header_out_month else R.string.transactions_header_out, money.compact(ready.model.expenseMinor)) + " · " +
                    pluralStringResource(R.plurals.transactions_expense_count, ready.model.expenseCount, ready.model.expenseCount)
            },
        ) {
            IconAction(ArcaIcons.search, stringResource(R.string.search_title), onOpenSearch)
            Box {
                IconAction(
                    ArcaIcons.filter,
                    stringResource(if (narrowed) R.string.filter_title_active else R.string.filter_title),
                    { filtering = true },
                    tint = if (narrowed) colors.ink else colors.inkSecondary,
                )
                if (narrowed) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-6).dp, y = 6.dp)
                            .size(13.dp)
                            .background(colors.surface, ArcaDesignSystem.shapes.pill)
                            .padding(2.dp)
                            .background(colors.accent, ArcaDesignSystem.shapes.pill),
                    )
                }
            }
        }

        if (narrowed) {
            Row(
                Modifier.padding(start = spacing.screenMargin, end = spacing.screenMargin, top = spacing.space4, bottom = spacing.space4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space3),
            ) {
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                    if (!thisMonth) item { RemovableChip(periodLabel(filter.period)) { onApplyFilter(filter.copy(period = PeriodChoice.Month(YearMonth.now()))) } }
                    if (filter.type != TypeFilter.ALL) item { RemovableChip(typeLabel(filter.type)) { onApplyFilter(filter.copy(type = TypeFilter.ALL)) } }
                    ready.accounts.filter { it.id in filter.accountIds }.forEach { account ->
                        item(key = "a-${account.id.value}") { RemovableChip(account.name) { onApplyFilter(filter.copy(accountIds = filter.accountIds - account.id)) } }
                    }
                    ready.categories.filter { it.id in filter.categoryIds }.forEach { category ->
                        item(key = "c-${category.id.value}") { RemovableChip(category.name) { onApplyFilter(filter.copy(categoryIds = filter.categoryIds - category.id)) } }
                    }
                }
                Text(
                    stringResource(R.string.filter_clear_all),
                    style = ArcaDesignSystem.typography.footnote,
                    color = colors.inkSecondary,
                    modifier = Modifier
                        .pressable(onResetFilter)
                        .padding(vertical = spacing.space2),
                )
            }
        } else {
            LazyRow(

                contentPadding = PaddingValues(horizontal = spacing.screenMargin),
                horizontalArrangement = Arrangement.spacedBy(spacing.space2),
                modifier = Modifier.padding(top = spacing.space4, bottom = spacing.space4),
            ) {
                item { TextChip(periodLabel(filter.period), selected = true, onClick = { filtering = true }) }
                item { TextChip(stringResource(R.string.filter_all_accounts), selected = false, onClick = { filtering = true }) }
                item { TextChip(stringResource(R.string.filter_all_categories), selected = false, onClick = { filtering = true }) }
            }
        }

        if (ready.model.days.isEmpty()) {
            Text(
                stringResource(R.string.transactions_no_match),
                style = ArcaDesignSystem.typography.subhead,
                color = colors.inkSecondary,
                modifier = Modifier.padding(spacing.screenMargin),
            )
        }
        TransactionDayList(ready.model.days, onOpenTransaction, onDelete)
    }

    if (filtering && ready != null) {
        FilterSheet(
            filter = ready.filter,
            accounts = ready.accounts.filterNot { it.isArchived },
            categories = ready.categories.filterNot { it.isArchived },
            resultCount = draftCount,
            onDraftChange = onDraftChange,
            onApply = {
                onApplyFilter(it)
                filtering = false
            },
            onReset = {
                onResetFilter()
                filtering = false
            },
            onDismiss = { filtering = false },
        )
    }
}

/** "GoPay ✕": an active filter that removes itself. */
@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    val colors = ArcaDesignSystem.colors
    Row(
        Modifier
            .pressable(onRemove, pressedScale = 0.94f, onClickLabel = stringResource(R.string.filter_remove, label))
            .background(colors.surfaceSunken, ArcaDesignSystem.shapes.pill)
            .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = ArcaDesignSystem.typography.footnote, color = colors.ink, maxLines = 1)
        ArcaIcon(ArcaIcons.close, tint = colors.inkTertiary, size = 14.dp)
    }
}

@Composable
private fun typeLabel(type: TypeFilter): String = stringResource(
    when (type) {
        TypeFilter.EXPENSE -> R.string.capture_mode_expense
        TypeFilter.INCOME -> R.string.capture_mode_income
        TypeFilter.TRANSFER -> R.string.capture_mode_transfer
        TypeFilter.ALL -> R.string.filter_all
    },
)

/**
 * Rows grouped under day headers with each day's net. With [onDelete], a row swipes left to
 * delete: past the line it asks first, then goes through the 5-second undo.
 */
@Composable
fun TransactionDayList(
    days: List<TransactionDay>,
    onOpenTransaction: (TransactionId) -> Unit,
    onDelete: ((Transaction) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val spacing = ArcaDesignSystem.spacing
    var confirming by remember { mutableStateOf<TransactionListItem?>(null) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        days.forEachIndexed { dayIndex, day ->
            val date = day.date
            if (date != null) {
                item(key = "day-$date", contentType = "day") {
                    DayHeader(dayLabel(date), day.netMinor, Modifier.padding(horizontal = spacing.screenMargin), first = dayIndex == 0)
                }
            }
            itemsIndexed(day.items, key = { _, item -> item.transaction.id.value }, contentType = { _, _ -> "row" }) { i, item ->
                Column {
                    if (i > 0) Hairline(Modifier.padding(end = spacing.screenMargin), startInset = spacing.screenMargin + 52.dp)
                    if (onDelete != null) {
                        SwipeToDelete(onRequestDelete = { confirming = item }) {
                            TransactionRow(item, onClick = { onOpenTransaction(item.transaction.id) }, modifier = Modifier.padding(horizontal = spacing.screenMargin))
                        }
                    } else {
                        TransactionRow(item, onClick = { onOpenTransaction(item.transaction.id) }, modifier = Modifier.padding(horizontal = spacing.screenMargin))
                    }
                }
            }
        }
    }
    val pending = confirming
    if (pending != null && onDelete != null) {
        ConfirmDeleteSheet(
            pending,
            onConfirm = {
                confirming = null
                onDelete(pending.transaction)
            },
            onDismiss = { confirming = null },
        )
    }
}
