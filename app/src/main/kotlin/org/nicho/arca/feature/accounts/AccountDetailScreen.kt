package org.nicho.arca.feature.accounts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.domain.formatting.MoneyFormat
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.TransactionId
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.readmodel.AccountDetailModel
import org.nicho.arca.domain.readmodel.BalancePoint
import org.nicho.arca.domain.readmodel.BalanceScale
import org.nicho.arca.domain.readmodel.PeriodChoice
import org.nicho.arca.domain.readmodel.TypeFilter
import org.nicho.arca.feature.reports.ArcaSheet
import org.nicho.arca.feature.reports.ChangeCaret
import org.nicho.arca.feature.transactions.periodLabel
import org.nicho.arca.ui.components.BarChart
import org.nicho.arca.ui.components.DateRangeSheet
import org.nicho.arca.ui.components.DetailSkeleton
import org.nicho.arca.ui.components.DetailTopBar
import org.nicho.arca.ui.components.IconAction
import org.nicho.arca.ui.components.MoneyFigure
import org.nicho.arca.ui.components.RollingText
import org.nicho.arca.ui.components.SectionLabel
import org.nicho.arca.ui.components.SegmentedControl
import org.nicho.arca.ui.components.SheetTitleBar
import org.nicho.arca.ui.components.TextChip
import org.nicho.arca.ui.components.TransactionRow
import org.nicho.arca.ui.components.pressable
import org.nicho.arca.ui.components.pushArrival
import org.nicho.arca.ui.format.appLocale
import org.nicho.arca.ui.format.monthLabel
import org.nicho.arca.ui.format.shortDate

@Composable
fun AccountDetailRoute(
    onBack: () -> Unit,
    onEdit: (AccountId) -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
    viewModel: AccountDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == AccountDetailUiState.Gone) onBack() }
    AccountDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = { onEdit(viewModel.accountId) },
        onOpenTransaction = onOpenTransaction,
        onPeriodChange = { viewModel.onPeriodChange(it) },
        onScaleChange = { viewModel.onScaleChange(it) },
        onFocus = { viewModel.onFocus(it) },
        onFilterChange = { viewModel.onFilterChange(it) },
        onToggleFavorite = { viewModel.onToggleFavorite() },
        onToggleArchived = { viewModel.onToggleArchived() },
    )
}

/**
 * Account Detail (board 08): the balance; a period ("October", "Last 3 months", a range…) whose
 * In / Out / Net, balance trend and history the rest of the screen shows; then that history under
 * a type filter that stays pinned. Touching a bar shows that bar's In / Out / Net as the finger
 * moves; letting go keeps it, narrowing the history to it too (client request, 2026-10-15). A
 * balance below zero gets a warning card with the way to fix it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountDetailScreen(
    state: AccountDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
    onPeriodChange: (PeriodChoice) -> Unit,
    onScaleChange: (BalanceScale) -> Unit,
    onFocus: (LocalDate?) -> Unit,
    onFilterChange: (TypeFilter) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleArchived: () -> Unit,
) {
    val spacing = ArcaDesignSystem.spacing
    var adjusting by rememberSaveable { mutableStateOf(false) }
    var choosingPeriod by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        DetailTopBar(onBack, actionLabel = stringResource(R.string.action_edit), onAction = onEdit)
        val ready = state as? AccountDetailUiState.Ready
        if (ready == null) {
            DetailSkeleton()
            return@Column
        }
        val model = ready.model
        // The bar under the finger, while one is.
        var hovered by remember(model.selection.period, model.scale) { mutableStateOf<Int?>(null) }
        val scope = scopeLabel(model)
        LazyColumn(
            Modifier
                .fillMaxSize()
                .pushArrival(),
            contentPadding = PaddingValues(bottom = spacing.space5),
        ) {
            item(key = "top", contentType = "top") {
                Column(Modifier.padding(start = spacing.screenMargin, end = spacing.screenMargin, bottom = spacing.space4)) {
                    Hero(model, onAdjust = { adjusting = true })
                    PeriodLine(model, onPeriodChange, onChoose = { choosingPeriod = true })
                    val point = hovered?.let { model.trend.getOrNull(it) } ?: model.focus
                    FlowFigures(model, point, onClear = { onFocus(null) })
                    BalanceTrend(model, hovered, onHover = { hovered = it }, onFocus = onFocus, onScaleChange = onScaleChange)
                    Row(Modifier.padding(top = spacing.space6), horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
                        QuietButton(
                            stringResource(if (model.account.isFavorite) R.string.accounts_unfavorite else R.string.accounts_make_favorite),
                            onToggleFavorite,
                            Modifier.weight(1f),
                        )
                        QuietButton(
                            stringResource(if (model.account.isArchived) R.string.accounts_unarchive else R.string.accounts_archive),
                            onToggleArchived,
                            Modifier.weight(1f),
                        )
                    }
                }
            }
            stickyHeader(key = "history", contentType = "header") { HistoryHeader(ready, scope, onFilterChange) }
            if (model.shownCount == 0) {
                item(key = "none", contentType = "none") {
                    Text(
                        stringResource(R.string.accounts_history_empty),
                        style = ArcaDesignSystem.typography.subhead,
                        color = ArcaDesignSystem.colors.inkSecondary,
                        modifier = Modifier.padding(start = spacing.screenMargin, end = spacing.screenMargin, top = spacing.space6),
                    )
                }
            }
            model.history.forEach { month ->
                item(key = "m-${month.month}", contentType = "month") {
                    Text(
                        monthYear(month.month).uppercase(appLocale()),
                        style = ArcaDesignSystem.typography.label,
                        color = ArcaDesignSystem.colors.inkSecondary,
                        modifier = Modifier.padding(start = spacing.screenMargin, end = spacing.screenMargin, top = spacing.space6),
                    )
                }
                month.days.forEach { day ->
                    item(key = "d-${day.date}", contentType = "day") {
                        Text(
                            day.date.format(DateTimeFormatter.ofPattern("MMM d", appLocale())),
                            style = ArcaDesignSystem.typography.footnote,
                            color = ArcaDesignSystem.colors.inkTertiary,
                            modifier = Modifier.padding(start = spacing.screenMargin, end = spacing.screenMargin, top = spacing.space4),
                        )
                    }
                    items(day.items, key = { it.transaction.id.value }, contentType = { "row" }) { item ->
                        TransactionRow(
                            item,
                            onClick = { onOpenTransaction(item.transaction.id) },
                            showAccount = false,
                            modifier = Modifier.padding(horizontal = spacing.screenMargin),
                        )
                    }
                }
            }
            item(key = "end") { Box(Modifier.navigationBarsPadding()) }
        }
    }
    val ready = state as? AccountDetailUiState.Ready
    if (adjusting && ready != null) AdjustBalanceSheet(ready.model.account.id, onDismiss = { adjusting = false })
    if (choosingPeriod && ready != null) {
        PeriodSheet(
            current = ready.model.selection.period,
            onPick = {
                choosingPeriod = false
                onPeriodChange(it)
            },
            onDismiss = { choosingPeriod = false },
        )
    }
}

@Composable
private fun Hero(model: AccountDetailModel, onAdjust: () -> Unit) {
    val colors = ArcaDesignSystem.colors
    val spacing = ArcaDesignSystem.spacing
    val account = model.account
    Column(Modifier.padding(top = spacing.space5)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
            AccountTile(account, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(account.name, style = ArcaDesignSystem.typography.headline, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    accountTypeLabel(account.type) + " · " + pluralStringResource(R.plurals.accounts_transaction_count, model.transactionCount, model.transactionCount),
                    style = ArcaDesignSystem.typography.footnote,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (account.isFavorite) {
                Text(
                    stringResource(R.string.accounts_favorite),
                    style = ArcaDesignSystem.typography.footnote,
                    color = colors.brand,
                    modifier = Modifier
                        .border(1.dp, colors.brand, ArcaDesignSystem.shapes.pill)
                        .padding(horizontal = spacing.space3, vertical = 5.dp),
                )
            }
        }
        MoneyFigure(
            model.balanceMinor,
            color = if (model.isNegative) colors.negative else colors.ink,
            rolling = true,
            modifier = Modifier.padding(top = spacing.space5),
        )
        Row(Modifier.padding(top = spacing.space3), verticalAlignment = Alignment.CenterVertically) {
            val opening = ArcaDesignSystem.money.amount(account.openingBalanceMinor)
            Text(
                model.firstDate?.let {
                    stringResource(R.string.accounts_opening_since, opening, it.format(DateTimeFormatter.ofPattern("MMM d, yyyy", appLocale())))
                } ?: stringResource(R.string.accounts_opening_balance, opening),
                style = ArcaDesignSystem.typography.footnote,
                color = colors.inkTertiary,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.adjust_action),
                style = ArcaDesignSystem.typography.footnote.copy(fontWeight = FontWeight.Medium),
                color = colors.ink,
                modifier = Modifier
                    .padding(start = spacing.space3)
                    .pressable(onAdjust),
            )
        }
        if (model.isNegative) {
            Row(
                Modifier
                    .padding(top = spacing.space4)
                    .fillMaxWidth()
                    .background(colors.negative.copy(alpha = 0.10f), ArcaDesignSystem.shapes.medium)
                    .pressable(onAdjust, pressedScale = 0.98f)
                    .padding(spacing.space4),
                horizontalArrangement = Arrangement.spacedBy(spacing.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArcaIcon(ArcaIcons.warning, tint = colors.negative, size = 20.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.accounts_negative_this), style = ArcaDesignSystem.typography.headline, color = colors.ink)
                    Text(stringResource(R.string.accounts_negative_this_help), style = ArcaDesignSystem.typography.footnote, color = colors.inkSecondary, modifier = Modifier.padding(top = 2.dp))
                }
                ArcaIcon(ArcaIcons.chevronRight, tint = colors.inkTertiary, size = 16.dp)
            }
        }
    }
}

/**
 * The period line, as Reports has it: "‹ October ▾ ›". The arrows step a month at a time when
 * the period is a month; the label opens the choice of periods.
 */
@Composable
private fun PeriodLine(model: AccountDetailModel, onPeriodChange: (PeriodChoice) -> Unit, onChoose: () -> Unit) {
    val colors = ArcaDesignSystem.colors
    val month = (model.selection.period as? PeriodChoice.Month)?.month
    val earliest = model.firstDate?.let(YearMonth::from)
    val current = YearMonth.now()
    Row(Modifier.padding(top = ArcaDesignSystem.spacing.space5), verticalAlignment = Alignment.CenterVertically) {
        IconAction(
            ArcaIcons.chevronLeft,
            stringResource(R.string.action_previous_month),
            { if (month != null) onPeriodChange(PeriodChoice.Month(month.minusMonths(1))) },
            size = 32.dp,
            enabled = month != null && (earliest == null || month > earliest),
            modifier = Modifier
                .offset(x = (-8).dp)
                .alpha(if (month == null) 0f else 1f),
        )
        Row(
            Modifier
                .weight(1f)
                .pressable(onChoose, pressedScale = 0.97f, onClickLabel = stringResource(R.string.accounts_choose_period)),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(periodLabel(model.selection.period), style = ArcaDesignSystem.typography.headline, color = colors.ink, maxLines = 1)
            ArcaIcon(ArcaIcons.chevronDown, tint = colors.inkTertiary, size = 16.dp)
        }
        IconAction(
            ArcaIcons.chevronRight,
            stringResource(R.string.action_next_month),
            { if (month != null) onPeriodChange(PeriodChoice.Month(month.plusMonths(1))) },
            size = 32.dp,
            enabled = month != null && month < current,
            modifier = Modifier
                .offset(x = 8.dp)
                .alpha(if (month == null) 0f else 1f),
        )
    }
}

/**
 * "IN AND OUT · OCTOBER" and the three figures: the whole period's, or the bar's under the finger
 * or picked. "Show all" goes back to the whole period.
 */
@Composable
private fun FlowFigures(model: AccountDetailModel, point: BalancePoint?, onClear: () -> Unit) {
    val colors = ArcaDesignSystem.colors
    val spacing = ArcaDesignSystem.spacing
    val flow = point?.flow ?: model.periodFlow
    val scope = point?.let { spanLabel(it.first, it.last, model.scale) } ?: periodLabel(model.selection.period)
    SectionLabel(
        stringResource(R.string.accounts_flow_label, scope),
        Modifier.padding(top = spacing.space5, bottom = spacing.space3),
        trailing = if (model.focus != null) stringResource(R.string.accounts_flow_clear) else null,
        onTrailingClick = onClear,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
        StatCell(stringResource(R.string.flow_in), flowFigure(flow.inMinor, TransactionType.INCOME), colors = Pair(colors.surfaceSunken, colors.income), Modifier.weight(1f))
        StatCell(stringResource(R.string.flow_out), flowFigure(flow.outMinor, TransactionType.EXPENSE), colors = Pair(colors.surfaceSunken, colors.ink), Modifier.weight(1f))
        StatCell(stringResource(R.string.flow_net), netFigure(flow.netMinor), colors = Pair(colors.surfaceHero, colors.inkOnHero), Modifier.weight(1f), hero = true)
    }
}

/** In / Out on a sunken block; Net on the hero fill, the one emphasised cell (DSG-ARC-001 §3.4a). */
@Composable
fun StatCell(label: String, value: String, colors: Pair<Color, Color>, modifier: Modifier = Modifier, hero: Boolean = false) {
    Column(
        modifier
            .background(colors.first, ArcaDesignSystem.shapes.medium)
            .padding(horizontal = ArcaDesignSystem.spacing.space3, vertical = ArcaDesignSystem.spacing.space4),
    ) {
        Text(
            label,
            style = ArcaDesignSystem.typography.footnote,
            color = if (hero) colors.second.copy(alpha = 0.75f) else ArcaDesignSystem.colors.inkSecondary,
        )
        Text(value, style = ArcaDesignSystem.typography.amountRow, color = colors.second, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}

/** "+8,000,000" in, "−7,827,500" out: the flow's direction is the label's, so the sign comes from the type. */
@Composable
fun flowText(amountMinor: Long, type: TransactionType): String =
    if (amountMinor == 0L) ArcaDesignSystem.money.digits(0) else ArcaDesignSystem.money.transaction(amountMinor, type)

/** [flowText], shortened to "+1.2M" when the full figure wouldn't fit a third of the row. */
@Composable
private fun flowFigure(amountMinor: Long, type: TransactionType): String {
    val full = flowText(amountMinor, type)
    if (full.length <= FIT) return full
    val short = ArcaDesignSystem.money.compact(amountMinor, withCurrency = false)
    return if (type == TransactionType.INCOME) "+$short" else MoneyFormat.MINUS + short
}

@Composable
private fun netFigure(amountMinor: Long): String {
    val full = ArcaDesignSystem.money.signed(amountMinor, withCurrency = false)
    return if (full.length <= FIT) full else ArcaDesignSystem.money.compactSigned(amountMinor)
}

private const val FIT = 11

/** A quiet sunken action ("Remove from favorites", "Archive account"). */
@Composable
private fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .pressable(onClick, pressedScale = 0.98f)
            .background(ArcaDesignSystem.colors.surfaceSunken, ArcaDesignSystem.shapes.medium)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = ArcaDesignSystem.typography.footnote, color = ArcaDesignSystem.colors.inkSecondary, maxLines = 1)
    }
}

/**
 * Balance Trend: a bar per day, week or month of the period (the grains it allows), the balance
 * at each bar's end. The readout names the bar under the finger, else the picked one, else the
 * newest, with its change on the bar before.
 */
@Composable
private fun BalanceTrend(
    model: AccountDetailModel,
    hovered: Int?,
    onHover: (Int?) -> Unit,
    onFocus: (LocalDate?) -> Unit,
    onScaleChange: (BalanceScale) -> Unit,
) {
    val colors = ArcaDesignSystem.colors
    val spacing = ArcaDesignSystem.spacing
    val points = model.trend
    if (points.isEmpty()) return
    Column(Modifier.padding(top = spacing.space6)) {
        Row(Modifier.height(34.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.accounts_balance_trend), Modifier.weight(1f))
            if (model.scales.size > 1) {
                SegmentedControl(
                    options = model.scales.map { scale ->
                        scale to stringResource(
                            when (scale) {
                                BalanceScale.DAILY -> R.string.reports_daily
                                BalanceScale.WEEKLY -> R.string.reports_weekly
                                BalanceScale.MONTHLY -> R.string.accounts_trend_monthly
                            },
                        )
                    },
                    selected = model.scale,
                    onSelect = onScaleChange,
                    compact = true,
                    modifier = Modifier.width((72 * model.scales.size).dp),
                )
            }
        }
        val focusIndex = model.focus?.let { f -> points.indexOfFirst { it.first == f.first } }?.takeIf { it >= 0 }
        val pick = points[(hovered ?: focusIndex ?: points.lastIndex).coerceIn(0, points.lastIndex)]
        Row(Modifier.padding(top = spacing.space3, bottom = spacing.space3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
            Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
                val strong = ArcaDesignSystem.typography.footnote.copy(fontWeight = FontWeight.Medium)
                val ink = if (pick.balanceMinor < 0) colors.negative else colors.ink
                Text("Rp", style = strong, color = ink)
                RollingText(ArcaDesignSystem.money.digits(pick.balanceMinor), strong, ink)
                Text(" · " + readName(pick, model.scale), style = ArcaDesignSystem.typography.footnote, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            pick.changePercent?.takeIf { it != 0 }?.let { change ->
                Row(
                    Modifier
                        .background(colors.surfaceSunken, ArcaDesignSystem.shapes.pill)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ChangeCaret(up = change > 0, color = colors.inkSecondary, size = 8.dp)
                    Text("${kotlin.math.abs(change)}%", style = ArcaDesignSystem.typography.footnote, color = colors.inkSecondary)
                }
            }
        }
        val spoken = points.map { spanLabel(it.first, it.last, model.scale) + ", " + ArcaDesignSystem.spokenMoney.format(it.balanceMinor) }
        BarChart(
            fractions = points.map { it.fraction },
            labels = barLabels(points, model.scale),
            spoken = spoken,
            selected = focusIndex,
            onSelect = { i -> onFocus(i?.let { points[it].first }) },
            onHover = onHover,
            replayKey = model.selection.period to model.scale,
            height = 88.dp,
            maxBarWidth = 28.dp,
            clearable = true,
        )
    }
}

/** Under the bars, about six labels from the newest back: the day, a week's Monday, the month. */
@Composable
private fun barLabels(points: List<BalancePoint>, scale: BalanceScale): List<String> {
    val locale = appLocale()
    val every = ((points.size + 5) / 6).coerceAtLeast(1)
    return points.mapIndexed { i, point ->
        if ((points.lastIndex - i) % every != 0) {
            ""
        } else {
            when (scale) {
                BalanceScale.DAILY -> point.last.dayOfMonth.toString()
                BalanceScale.WEEKLY -> point.first.month.getDisplayName(TextStyle.SHORT, locale) + " " + point.first.dayOfMonth
                BalanceScale.MONTHLY -> point.first.month.getDisplayName(TextStyle.SHORT, locale)
            }
        }
    }
}

/** What a bar covers: "Wed, Oct 15", "Oct 13–19", "Sep 29 – Oct 5", "September". */
@Composable
private fun spanLabel(first: LocalDate, last: LocalDate, scale: BalanceScale): String {
    val locale = appLocale()
    return when {
        first == last -> first.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) + ", " + shortDate(first)
        scale == BalanceScale.MONTHLY && first.dayOfMonth == 1 && last == first.withDayOfMonth(first.lengthOfMonth()) -> monthLabel(YearMonth.from(first))
        first.month == last.month -> shortDate(first) + "–" + last.dayOfMonth
        else -> shortDate(first) + " – " + shortDate(last)
    }
}

/** The readout's words for a bar: "Today, Oct 15" for the bar that ends today, else what it covers. */
@Composable
private fun readName(point: BalancePoint, scale: BalanceScale): String =
    if (point.last == LocalDate.now()) stringResource(R.string.accounts_trend_today, shortDate(point.last)) else spanLabel(point.first, point.last, scale)

/** What the figures and history cover: the picked bar, or the whole period. */
@Composable
private fun scopeLabel(model: AccountDetailModel): String =
    model.focus?.let { spanLabel(it.first, it.last, model.scale) } ?: periodLabel(model.selection.period)

/**
 * The pinned header over the history: "HISTORY · OCTOBER · 21 transactions" and All / Income /
 * Expenses / Transfers, on the page colour with a rule under it.
 */
@Composable
private fun HistoryHeader(ready: AccountDetailUiState.Ready, scope: String, onFilterChange: (TypeFilter) -> Unit) {
    val colors = ArcaDesignSystem.colors
    val spacing = ArcaDesignSystem.spacing
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(top = spacing.space1),
    ) {
        Column(Modifier.padding(horizontal = spacing.screenMargin, vertical = spacing.space3)) {
            Row(verticalAlignment = Alignment.Bottom) {
                SectionLabel(stringResource(R.string.accounts_history) + " · " + scope, Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingText(ready.model.shownCount.toString(), ArcaDesignSystem.typography.footnote, colors.inkTertiary)
                    Text(
                        " " + pluralStringResource(R.plurals.accounts_transactions_word, ready.model.shownCount),
                        style = ArcaDesignSystem.typography.footnote,
                        color = colors.inkTertiary,
                    )
                }
            }
            Row(Modifier.padding(top = spacing.space3), horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                listOf(
                    TypeFilter.ALL to R.string.filter_all,
                    TypeFilter.INCOME to R.string.filter_income,
                    TypeFilter.EXPENSE to R.string.filter_expenses,
                    TypeFilter.TRANSFER to R.string.filter_transfers,
                ).forEach { (type, label) ->
                    TextChip(stringResource(label), ready.filter == type, { onFilterChange(type) })
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
    }
}

/**
 * Which stretch the account is looked at over: this or last month, the last 30 days, 3 or 12
 * months, all time, or a range from the calendar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodSheet(current: PeriodChoice, onPick: (PeriodChoice) -> Unit, onDismiss: () -> Unit) {
    val spacing = ArcaDesignSystem.spacing
    val colors = ArcaDesignSystem.colors
    var pickingRange by remember { mutableStateOf(false) }
    val thisMonth = YearMonth.now()
    val range = current as? PeriodChoice.Range
    ArcaSheet(onDismiss) {
        SheetTitleBar(stringResource(R.string.filter_period), onDismiss)
        FlowRow(
            Modifier.padding(start = spacing.space5, end = spacing.space5, top = spacing.space5, bottom = spacing.space6),
            horizontalArrangement = Arrangement.spacedBy(spacing.space2),
            verticalArrangement = Arrangement.spacedBy(spacing.space2),
        ) {
            listOf(
                PeriodChoice.Month(thisMonth) to stringResource(R.string.filter_this_month),
                PeriodChoice.Month(thisMonth.minusMonths(1)) to stringResource(R.string.filter_last_month),
                PeriodChoice.LastDays(30) to stringResource(R.string.filter_last_30_days),
                PeriodChoice.LastDays(90) to stringResource(R.string.filter_last_3_months),
                PeriodChoice.LastDays(365) to stringResource(R.string.period_last_12_months),
                PeriodChoice.AllTime to stringResource(R.string.period_all_time),
            ).forEach { (choice, label) ->
                TextChip(label, selected = current == choice, onClick = { onPick(choice) }, large = true)
            }
            TextChip(
                if (range != null) periodLabel(range) else stringResource(R.string.filter_range),
                selected = range != null,
                onClick = { pickingRange = true },
                large = true,
                dashed = true,
                trailing = { ArcaIcon(ArcaIcons.calendar, tint = if (range != null) colors.accentOn else colors.inkSecondary, size = 15.dp) },
            )
        }
    }
    if (pickingRange) {
        DateRangeSheet(
            initialFrom = range?.from,
            initialTo = range?.to,
            today = LocalDate.now(),
            onApply = { from, to ->
                pickingRange = false
                onPick(PeriodChoice.Range(from, to))
            },
            onDismiss = { pickingRange = false },
        )
    }
}

@Composable
private fun monthYear(month: YearMonth): String =
    month.month.getDisplayName(TextStyle.FULL_STANDALONE, appLocale()) + " " + month.year
