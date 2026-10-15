package org.nicho.arca.feature.budget

import org.nicho.arca.ui.components.BudgetSkeleton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.domain.calculation.EnvelopeCalculator
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.model.Envelope
import org.nicho.arca.domain.readmodel.BudgetModel
import org.nicho.arca.domain.readmodel.MonthState
import org.nicho.arca.ui.components.ArchivedSection
import org.nicho.arca.ui.components.EnvelopeRow
import org.nicho.arca.ui.components.Hairline
import org.nicho.arca.ui.components.IconAction
import org.nicho.arca.ui.components.IconTile
import org.nicho.arca.ui.components.LockBadge
import org.nicho.arca.ui.components.MoneyFigure
import org.nicho.arca.ui.components.PeriodSwitcher
import org.nicho.arca.ui.components.ProgressBar
import org.nicho.arca.ui.components.ScreenHeader
import org.nicho.arca.ui.components.SectionLabel
import org.nicho.arca.ui.components.SegmentedControl
import org.nicho.arca.ui.components.arriving
import org.nicho.arca.ui.components.pressable
import org.nicho.arca.ui.components.rememberPageArrival
import org.nicho.arca.ui.format.appLocale
import org.nicho.arca.ui.format.shortDate

@Composable
fun BudgetRoute(
    onOpenCategory: (CategoryId, YearMonth) -> Unit,
    onEditCategory: (CategoryId?) -> Unit,
    onOpenReport: (YearMonth) -> Unit,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BudgetScreen(state, viewModel::onMonthChange, onOpenCategory, onEditCategory, onOpenReport)
}

private enum class BudgetView { EXPENSES, INCOME }

/**
 * The Budget tab (board 02): a month navigator, what's left to spend, then the envelopes (or
 * what was received), the categories without a budget, and the archived ones folded away.
 */
@Composable
fun BudgetScreen(
    state: BudgetUiState,
    onMonthChange: (YearMonth) -> Unit,
    onOpenCategory: (CategoryId, YearMonth) -> Unit,
    onEditCategory: (CategoryId?) -> Unit,
    onOpenReport: (YearMonth) -> Unit,
) {
    val spacing = ArcaDesignSystem.spacing
    var view by rememberSaveable { mutableStateOf(BudgetView.EXPENSES) }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        ScreenHeader(stringResource(R.string.nav_budget)) {
            IconAction(ArcaIcons.add, stringResource(R.string.category_new_title), { onEditCategory(null) }, tint = ArcaDesignSystem.colors.ink)
        }
        val ready = state as? BudgetUiState.Ready
        if (ready == null) {
            BudgetSkeleton()
            return@Column
        }
        val model = ready.model
        val arrival = rememberPageArrival()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenMargin)
                .padding(bottom = 110.dp)
                .navigationBarsPadding(),
        ) {
            PeriodSwitcher(
                ready.month,
                onMonthChange,
                Modifier
                    .padding(top = spacing.space2)
                    .arriving(arrival, 0),
                latest = ready.currentMonth.plusMonths(1),
            )
            BudgetHero(model, ready.month, Modifier.arriving(arrival, 1))
            SegmentedControl(
                options = listOf(
                    BudgetView.EXPENSES to stringResource(R.string.budget_view_expenses),
                    BudgetView.INCOME to stringResource(R.string.budget_view_income),
                ),
                selected = view,
                onSelect = { view = it },
                modifier = Modifier
                    .padding(top = spacing.space6)
                    .arriving(arrival, 2),
            )
            Column(
                Modifier
                    .padding(top = spacing.space5)
                    .arriving(arrival, 3),
            ) {
                if (view == BudgetView.EXPENSES) {
                    Expenses(ready, onOpenCategory, onEditCategory)
                } else {
                    Income(ready, onOpenCategory)
                }
            }
            SpendingReportRow(
                onClick = { onOpenReport(ready.month) },
                modifier = Modifier
                    .padding(top = spacing.space6)
                    .arriving(arrival, 4),
            )
        }
    }
}

/**
 * What's left in the month (FR-263), draining full → empty. The total is information, never an
 * input: it changes only through envelopes. Overspent in total, it reads "Over by" in the
 * negative colour (client decision, 2026-10-14).
 */
@Composable
private fun BudgetHero(model: BudgetModel, month: YearMonth, modifier: Modifier = Modifier) {
    val colors = ArcaDesignSystem.colors
    val spacing = ArcaDesignSystem.spacing
    val money = ArcaDesignSystem.money
    val summary = model.summary
    val over = summary.totalLeftMinor < 0
    Column(modifier.padding(top = spacing.space4)) {
        Row(Modifier.padding(bottom = spacing.space3), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(
                stringResource(
                    when {
                        over -> R.string.budget_hero_over
                        model.monthState == MonthState.CLOSED -> R.string.budget_hero_left_over
                        model.monthState == MonthState.AHEAD -> R.string.budget_hero_available
                        else -> R.string.budget_left_to_spend
                    },
                ),
                Modifier.weight(1f),
            )
            Text(heroMeta(model, month), style = ArcaDesignSystem.typography.footnote, color = colors.inkTertiary)
        }
        if (summary.envelopes.isEmpty()) {
            Text(
                stringResource(R.string.budget_no_envelopes),
                style = ArcaDesignSystem.typography.subhead,
                color = colors.inkSecondary,
            )
            return@Column
        }
        MoneyFigure(
            summary.totalLeftMinor,
            color = if (over) colors.negative else colors.ink,
            magnitude = true,
            rolling = true,
        )
        ProgressBar(
            EnvelopeCalculator.leftFraction(summary.totalAvailableMinor, summary.totalLeftMinor),
            colors.accent,
            Modifier.padding(top = spacing.space4),
            delayMillis = 160,
            replayKey = month,
        )
        Text(
            stringResource(R.string.budget_hero_meta, money.amount(summary.totalSpentInEnvelopesMinor), money.amount(summary.totalAvailableMinor)),
            style = ArcaDesignSystem.typography.footnote,
            color = colors.inkSecondary,
            modifier = Modifier.padding(top = spacing.space3),
        )
        val note = if (model.monthState == MonthState.CLOSED) {
            val next = month.plusMonths(1)
            buildString {
                append(stringResource(R.string.budget_closed_note, money.amount(summary.rollsOutMinor), monthName(next), money.amount(summary.resetsMinor), shortDate(next.atDay(1))))
                if (summary.overspentMinor > 0) append(" ").append(stringResource(R.string.budget_closed_over, money.amount(summary.overspentMinor)))
            }
        } else if (summary.carriedInMinor > 0) {
            stringResource(R.string.budget_total_help_carried, money.amount(summary.carriedInMinor))
        } else {
            stringResource(R.string.budget_total_help)
        }
        Text(note, style = ArcaDesignSystem.typography.footnote, color = colors.inkTertiary, modifier = Modifier.padding(top = spacing.space2))
    }
}

/** "Closed", "Ends today", "12 days left", "Starts Oct 1". */
@Composable
private fun heroMeta(model: BudgetModel, month: YearMonth): String = when (model.monthState) {
    MonthState.CLOSED -> stringResource(R.string.budget_meta_closed)
    MonthState.AHEAD -> stringResource(R.string.budget_meta_starts, shortDate(month.atDay(1)))
    MonthState.CURRENT -> {
        val days = ChronoUnit.DAYS.between(model.today, month.atEndOfMonth()).toInt()
        if (days <= 0) stringResource(R.string.budget_meta_ends_today) else pluralStringResource(R.plurals.budget_meta_days_left, days, days)
    }
}

@Composable
private fun Expenses(
    ready: BudgetUiState.Ready,
    onOpenCategory: (CategoryId, YearMonth) -> Unit,
    onEditCategory: (CategoryId?) -> Unit,
) {
    val model = ready.model
    val summary = model.summary
    val colors = ArcaDesignSystem.colors
    val spacing = ArcaDesignSystem.spacing
    val money = ArcaDesignSystem.money
    if (summary.envelopes.isNotEmpty()) {
        SectionLabel(stringResource(R.string.budget_budgeted), Modifier.padding(bottom = spacing.space1))
        summary.envelopes.forEachIndexed { index, envelope ->
            val category = model.categories[envelope.categoryId]
            val (footnote, rollLine) = envelopeLines(envelope, ready.month, model.monthState)
            EnvelopeRow(
                name = category?.name.orEmpty(),
                envelope = envelope,
                hue = category?.let { colors.category(it.colorHex) } ?: colors.inkSecondary,
                icon = ArcaIcons.named(category?.icon ?: "tag"),
                onClick = { onOpenCategory(envelope.categoryId, ready.month) },
                leftWord = stringResource(
                    when (model.monthState) {
                        MonthState.CLOSED -> R.string.budget_word_unspent
                        MonthState.AHEAD -> R.string.budget_word_available
                        MonthState.CURRENT -> R.string.budget_left
                    },
                ),
                footnote = footnote,
                rollLine = rollLine,
                index = index,
                replayKey = ready.month,
            )
            Hairline(startInset = 52.dp)
        }
    }
    if (summary.notBudgeted.isNotEmpty()) {
        SectionLabel(
            stringResource(R.string.budget_not_budgeted),
            Modifier.padding(top = if (summary.envelopes.isEmpty()) 0.dp else spacing.space6, bottom = spacing.space1),
        )
        summary.notBudgeted.forEach { spend ->
            val category = model.categories[spend.categoryId]
            val locked = category?.isSystemLocked == true
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .pressable({ onOpenCategory(spend.categoryId, ready.month) }, pressedScale = 1f)
                        .padding(vertical = spacing.space3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        IconTile(ArcaIcons.named(spend.icon), if (locked) colors.inkSecondary else colors.category(spend.colorHex))
                        if (locked) LockBadge(Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp))
                    }
                    Column(Modifier.padding(start = spacing.space3)) {
                        Text(spend.name, style = ArcaDesignSystem.typography.headline, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(if (locked) R.string.budget_spent_never else R.string.budget_spent, money.amount(spend.totalMinor)),
                            style = ArcaDesignSystem.typography.footnote,
                            color = colors.inkTertiary,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                }
                if (!locked) {
                    Text(
                        stringResource(R.string.budget_set_budget),
                        style = ArcaDesignSystem.typography.footnote.copy(fontWeight = FontWeight.Medium),
                        color = colors.ink,
                        modifier = Modifier
                            .pressable({ onEditCategory(spend.categoryId) })
                            .padding(start = spacing.space3, top = 13.dp, bottom = 13.dp),
                    )
                }
            }
            Hairline(startInset = 52.dp)
        }
        Text(
            stringResource(R.string.budget_not_counted),
            style = ArcaDesignSystem.typography.footnote,
            color = colors.inkTertiary,
            modifier = Modifier.padding(top = spacing.space3),
        )
    }
    val archived = model.categories.values.filter { it.isArchived && it.kind == CategoryKind.EXPENSE }.sortedBy { it.name }
    if (archived.isNotEmpty()) {
        ArchivedSection(
            title = stringResource(R.string.budget_archived, archived.size),
            help = stringResource(R.string.budget_archived_help),
        ) {
            archived.forEach { category ->
                val count = model.archivedCounts[category.id] ?: 0
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable({ onOpenCategory(category.id, ready.month) }, pressedScale = 1f)
                        .padding(vertical = spacing.space3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconTile(ArcaIcons.named(category.icon), colors.category(category.colorHex), Modifier.alpha(0.55f))
                    Column(Modifier.padding(start = spacing.space3)) {
                        Text(category.name, style = ArcaDesignSystem.typography.headline, color = colors.inkSecondary)
                        Text(
                            pluralStringResource(R.plurals.category_transaction_count, count, count),
                            style = ArcaDesignSystem.typography.footnote,
                            color = colors.inkTertiary,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                }
                Hairline(startInset = 52.dp)
            }
        }
    }
}

@Composable
private fun Income(ready: BudgetUiState.Ready, onOpenCategory: (CategoryId, YearMonth) -> Unit) {
    val model = ready.model
    val colors = ArcaDesignSystem.colors
    val spacing = ArcaDesignSystem.spacing
    val money = ArcaDesignSystem.money
    Row(Modifier.padding(bottom = spacing.space1), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(stringResource(R.string.budget_received), Modifier.weight(1f))
        Text(
            if (model.receivedMinor > 0) money.signed(model.receivedMinor, withCurrency = false) else money.amount(0),
            style = ArcaDesignSystem.typography.amountRow,
            color = if (model.receivedMinor > 0) colors.income else colors.inkTertiary,
        )
    }
    model.incomeRows.forEach { row ->
        val received = row.totalMinor > 0
        Row(
            Modifier
                .fillMaxWidth()
                .pressable({ onOpenCategory(row.category.id, ready.month) }, pressedScale = 1f)
                .padding(vertical = spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(ArcaIcons.named(row.category.icon), colors.income)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = spacing.space3),
            ) {
                Text(row.category.name, style = ArcaDesignSystem.typography.headline, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        row.count > 0 -> pluralStringResource(R.plurals.category_transaction_count, row.count, row.count)
                        model.monthState == MonthState.AHEAD -> stringResource(R.string.budget_no_transactions_yet)
                        else -> stringResource(R.string.budget_no_transactions)
                    },
                    style = ArcaDesignSystem.typography.footnote,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Text(
                if (received) money.signed(row.totalMinor, withCurrency = false) else money.amount(0),
                style = ArcaDesignSystem.typography.amountRow,
                color = if (received) colors.income else colors.inkTertiary,
            )
        }
        Hairline(startInset = 52.dp)
    }
    Text(
        stringResource(R.string.budget_income_help),
        style = ArcaDesignSystem.typography.footnote,
        color = colors.inkTertiary,
        modifier = Modifier.padding(top = spacing.space3),
    )
}

/** "Spending report ›", between two rules: the way into Reports from here. */
@Composable
private fun SpendingReportRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ArcaDesignSystem.colors
    Column(modifier) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .pressable(onClick, pressedScale = 1f)
                .padding(vertical = ArcaDesignSystem.spacing.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArcaIcon(ArcaIcons.reports, tint = colors.inkSecondary)
            Text(
                stringResource(R.string.budget_spending_report),
                style = ArcaDesignSystem.typography.body,
                color = colors.ink,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = ArcaDesignSystem.spacing.space3),
            )
            ArcaIcon(ArcaIcons.chevronRight, tint = colors.inkTertiary, size = 18.dp)
        }
        Hairline()
    }
}

/**
 * The reset line beside "Spent X of Y" and the roll-over line under it, by where the month stands:
 * "Resets Oct 1" / "Reset Sep 1"; "Rolls over · +Rp100,000 from Aug", "Rolls over · nothing
 * carried in", or for a closed month "Rolled over · Rp120,000 into Sep".
 */
@Composable
fun envelopeLines(envelope: Envelope, month: YearMonth, state: MonthState): Pair<String?, String?> {
    val money = ArcaDesignSystem.money
    val next = month.plusMonths(1)
    if (!envelope.rollsOver) {
        val date = shortDate(next.atDay(1))
        return stringResource(if (state == MonthState.CLOSED) R.string.budget_reset_past else R.string.budget_resets, date) to null
    }
    val line = when {
        state == MonthState.CLOSED && envelope.leftMinor > 0 ->
            stringResource(R.string.budget_rolled_into, money.amount(envelope.leftMinor), monthShort(next))
        state == MonthState.CLOSED && envelope.leftMinor < 0 -> stringResource(R.string.budget_rolls_over_overspent)
        state == MonthState.CLOSED -> stringResource(R.string.budget_rolls_over_nothing_left)
        envelope.carryMinor > 0 -> stringResource(R.string.budget_rolls_over_from, money.amount(envelope.carryMinor), monthShort(month.minusMonths(1)))
        else -> stringResource(R.string.budget_rolls_over_nothing)
    }
    return null to line
}

@Composable
private fun monthShort(month: YearMonth): String = month.month.getDisplayName(TextStyle.SHORT, appLocale())

@Composable
private fun monthName(month: YearMonth): String = month.month.getDisplayName(TextStyle.FULL_STANDALONE, appLocale())
