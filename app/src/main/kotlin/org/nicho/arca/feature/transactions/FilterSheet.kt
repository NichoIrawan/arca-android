package org.nicho.arca.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.readmodel.PeriodChoice
import org.nicho.arca.domain.readmodel.SortOrder
import org.nicho.arca.domain.readmodel.TransactionFilter
import org.nicho.arca.domain.readmodel.TypeFilter
import org.nicho.arca.feature.reports.ArcaSheet
import org.nicho.arca.ui.components.DateRangeSheet
import org.nicho.arca.ui.components.Hairline
import org.nicho.arca.ui.components.PrimaryButton
import org.nicho.arca.ui.components.SectionLabel
import org.nicho.arca.ui.components.SegmentedControl
import org.nicho.arca.ui.components.SheetTitleBar
import org.nicho.arca.ui.components.TextChip
import org.nicho.arca.ui.components.pressable
import org.nicho.arca.ui.format.monthLabel
import org.nicho.arca.ui.format.shortDate

/**
 * Filter (board 12, FR-108): type, period, accounts, categories and sort, all combinable. Edits a
 * draft, and the Apply button says how many rows that draft would show before anything changes.
 * Long account and category lists show their first few with "See all".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    filter: TransactionFilter,
    accounts: List<Account>,
    categories: List<Category>,
    resultCount: Int?,
    onDraftChange: (TransactionFilter) -> Unit,
    onApply: (TransactionFilter) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = ArcaDesignSystem.spacing
    val colors = ArcaDesignSystem.colors
    var draft by remember { mutableStateOf(filter) }
    var pickingRange by rememberSaveable { mutableStateOf(false) }
    var allAccounts by rememberSaveable { mutableStateOf(false) }
    var allCategories by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(draft) { onDraftChange(draft) }
    val thisMonth = YearMonth.now()
    val default = TransactionFilter(PeriodChoice.Month(thisMonth))

    ArcaSheet(onDismiss) {
        SheetTitleBar(stringResource(R.string.filter_title), onDismiss) {
            Text(
                stringResource(R.string.filter_reset),
                style = ArcaDesignSystem.typography.body,
                color = if (draft == default) colors.inkTertiary else colors.ink,
                modifier = Modifier
                    .pressable({ draft = default; onReset() }, enabled = draft != default, disabledAlpha = 1f)
                    .padding(start = spacing.space3, top = spacing.space2, bottom = spacing.space2),
            )
        }
        SegmentedControl(
            options = listOf(
                TypeFilter.ALL to stringResource(R.string.filter_all),
                TypeFilter.EXPENSE to stringResource(R.string.capture_mode_expense),
                TypeFilter.INCOME to stringResource(R.string.capture_mode_income),
                TypeFilter.TRANSFER to stringResource(R.string.capture_mode_transfer),
            ),
            selected = draft.type,
            onSelect = { draft = draft.copy(type = it) },
            modifier = Modifier.padding(start = spacing.space5, end = spacing.space5, top = spacing.space5),
        )
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = spacing.space5, end = spacing.space5, bottom = spacing.space5),
        ) {
            SectionLabel(stringResource(R.string.filter_period), Modifier.padding(top = spacing.space6, bottom = spacing.space3))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.space2), verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                listOf(
                    PeriodChoice.Month(thisMonth) to stringResource(R.string.filter_this_month),
                    PeriodChoice.Month(thisMonth.minusMonths(1)) to stringResource(R.string.filter_last_month),
                    PeriodChoice.LastDays(30) to stringResource(R.string.filter_last_30_days),
                    PeriodChoice.LastDays(90) to stringResource(R.string.filter_last_3_months),
                ).forEach { (choice, label) ->
                    TextChip(label, selected = draft.period == choice, onClick = { draft = draft.copy(period = choice) }, large = true)
                }
                val range = draft.period as? PeriodChoice.Range
                TextChip(
                    if (range != null) periodLabel(range) else stringResource(R.string.filter_range),
                    selected = range != null,
                    onClick = { pickingRange = true },
                    large = true,
                    dashed = true,
                    trailing = { ArcaIcon(ArcaIcons.calendar, tint = if (range != null) colors.accentOn else colors.inkSecondary, size = 15.dp) },
                )
            }

            ChoiceSection(
                title = stringResource(R.string.nav_accounts),
                selectedCount = draft.accountIds.size,
                total = accounts.size,
                showAll = allAccounts,
                onShowAll = { allAccounts = !allAccounts },
                limit = 6,
                items = accounts,
            ) { account ->
                val selected = account.id in draft.accountIds
                TextChip(account.name, selected, onClick = {
                    draft = draft.copy(accountIds = if (selected) draft.accountIds - account.id else draft.accountIds + account.id)
                }, large = true)
            }

            ChoiceSection(
                title = stringResource(R.string.filter_categories),
                selectedCount = draft.categoryIds.size,
                total = categories.size,
                showAll = allCategories,
                onShowAll = { allCategories = !allCategories },
                limit = 8,
                items = categories,
            ) { category ->
                val selected = category.id in draft.categoryIds
                TextChip(
                    category.name,
                    selected,
                    onClick = { draft = draft.copy(categoryIds = if (selected) draft.categoryIds - category.id else draft.categoryIds + category.id) },
                    large = true,
                    leading = {
                        if (category.isSystemLocked) {
                            ArcaIcon(ArcaIcons.lock, tint = if (selected) colors.accentOn else colors.inkSecondary, size = 13.dp)
                        } else {
                            Box(Modifier.size(7.dp).background(colors.category(category.colorHex), ArcaDesignSystem.shapes.pill))
                        }
                    },
                )
            }

            SectionLabel(stringResource(R.string.filter_sort), Modifier.padding(top = spacing.space6, bottom = spacing.space3))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                listOf(
                    SortOrder.NEWEST to R.string.sort_newest,
                    SortOrder.OLDEST to R.string.sort_oldest,
                    SortOrder.LARGEST to R.string.sort_largest,
                ).forEach { (order, label) ->
                    TextChip(stringResource(label), draft.sort == order, { draft = draft.copy(sort = order) }, large = true)
                }
            }
        }
        Hairline()
        PrimaryButton(
            if (resultCount != null) pluralStringResource(R.plurals.filter_apply_results, resultCount, resultCount) else stringResource(R.string.filter_apply),
            { onApply(draft) },
            Modifier.padding(start = spacing.space5, end = spacing.space5, top = spacing.space4, bottom = spacing.space5),
        )
    }

    if (pickingRange) {
        val range = draft.period as? PeriodChoice.Range
        DateRangeSheet(
            initialFrom = range?.from,
            initialTo = range?.to,
            today = LocalDate.now(),
            onApply = { from, to ->
                draft = draft.copy(period = PeriodChoice.Range(from, to))
                pickingRange = false
            },
            onDismiss = { pickingRange = false },
        )
    }
}

/** A labelled group of chips: "ACCOUNTS ……… 1 selected · 6 of 9", the first few, then "See all ⌄". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceSection(
    title: String,
    selectedCount: Int,
    total: Int,
    showAll: Boolean,
    onShowAll: () -> Unit,
    limit: Int,
    items: List<T>,
    chip: @Composable (T) -> Unit,
) {
    val spacing = ArcaDesignSystem.spacing
    val colors = ArcaDesignSystem.colors
    val shown = if (showAll || items.size <= limit) items else items.take(limit)
    Row(Modifier.padding(top = spacing.space6, bottom = spacing.space3), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(title, Modifier.weight(1f))
        if (items.size > limit) {
            Text(
                stringResource(R.string.filter_selected_meta, selectedCount, shown.size, total),
                style = ArcaDesignSystem.typography.footnote,
                color = colors.inkTertiary,
            )
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.space2), verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
        shown.forEach { chip(it) }
        if (items.size > limit) {
            TextChip(
                stringResource(if (showAll) R.string.filter_show_less else R.string.filter_see_all),
                selected = false,
                onClick = onShowAll,
                large = true,
                dashed = true,
                trailing = {
                    ArcaIcon(
                        ArcaIcons.chevronDown,
                        tint = colors.inkSecondary,
                        size = 14.dp,
                        modifier = Modifier.rotate(if (showAll) 180f else 0f),
                    )
                },
            )
        }
    }
}

/** "September", "Last 30 days", "Sep 1 – Sep 30". */
@Composable
fun periodLabel(choice: PeriodChoice): String = when (choice) {
    is PeriodChoice.Month -> monthLabel(choice.month)
    is PeriodChoice.LastDays -> pluralStringResource(R.plurals.filter_last_n_days, choice.days, choice.days)
    is PeriodChoice.Range -> shortDate(choice.from) + " – " + shortDate(choice.to)
    PeriodChoice.AllTime -> stringResource(R.string.period_all_time)
}
