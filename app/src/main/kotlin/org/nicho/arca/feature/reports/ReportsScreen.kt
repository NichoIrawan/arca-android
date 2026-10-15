package org.nicho.arca.feature.reports

import org.nicho.arca.ui.components.ReportsSkeleton
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.readmodel.ReportCategory
import org.nicho.arca.domain.readmodel.MetricTotal
import org.nicho.arca.domain.readmodel.ReportMetric
import org.nicho.arca.ui.components.BarChart
import org.nicho.arca.ui.components.BarColors
import org.nicho.arca.ui.components.TextChip
import org.nicho.arca.ui.components.neutralBarColors
import org.nicho.arca.domain.readmodel.ReportRange
import org.nicho.arca.domain.readmodel.ReportsModel
import org.nicho.arca.domain.readmodel.TrendGranularity
import org.nicho.arca.ui.components.DetailTopBar
import org.nicho.arca.ui.components.Hairline
import org.nicho.arca.ui.components.IconAction
import org.nicho.arca.ui.components.MoneyFigure
import org.nicho.arca.ui.components.PrimaryButton
import org.nicho.arca.ui.components.ProgressBar
import org.nicho.arca.ui.components.RollingText
import org.nicho.arca.ui.components.SectionLabel
import org.nicho.arca.ui.components.SegmentedControl
import org.nicho.arca.ui.components.pressable
import org.nicho.arca.ui.components.pushArrival
import org.nicho.arca.ui.format.appLocale

@Composable
fun ReportsRoute(
    onBack: () -> Unit,
    onOpenCategory: (CategoryId, YearMonth) -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val export by viewModel.export.collectAsStateWithLifecycle()
    ReportsScreen(
        state = state,
        export = export,
        onBack = onBack,
        onRangeChange = { viewModel.onRangeChange(it) },
        onGranularityChange = { viewModel.onGranularityChange(it) },
        onMetricChange = { viewModel.onMetricChange(it) },
        onOpenCategory = onOpenCategory,
        onExport = { span, month, earliest -> viewModel.onExport(span, month, earliest) },
        onExportDismissed = { viewModel.onExportDismissed() },
    )
}

private enum class RangeChoice { WEEK, FOUR_WEEKS, MONTH }

/** Reports (board 06). */
@Composable
fun ReportsScreen(
    state: ReportsUiState,
    export: ExportState,
    onBack: () -> Unit,
    onRangeChange: (ReportRange) -> Unit,
    onGranularityChange: (TrendGranularity) -> Unit,
    onMetricChange: (ReportMetric) -> Unit,
    onOpenCategory: (CategoryId, YearMonth) -> Unit,
    onExport: (ExportSpan, YearMonth, YearMonth?) -> Unit,
    onExportDismissed: () -> Unit,
) {
    val spacing = ArcaDesignSystem.spacing
    var exporting by rememberSaveable { mutableStateOf(false) }
    var choosingMonth by rememberSaveable { mutableStateOf(false) }
    val model = (state as? ReportsUiState.Ready)?.model
    val metric = (state as? ReportsUiState.Ready)?.metric ?: ReportMetric.EXPENSES
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        DetailTopBar(
            onBack,
            actionIcon = ArcaIcons.download,
            actionIconDescription = stringResource(R.string.reports_export),
            onAction = { exporting = true },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .pushArrival()
                .padding(horizontal = spacing.screenMargin)
                .padding(bottom = spacing.space6)
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(R.string.reports_title),
                style = ArcaDesignSystem.typography.title,
                color = ArcaDesignSystem.colors.ink,
                modifier = Modifier
                    .padding(top = spacing.space4)
                    .semantics { heading() },
            )
            if (model == null) {
                ReportsSkeleton()
                return@Column
            }
            ReportsContent(model, metric, onRangeChange, onGranularityChange, onMetricChange, onOpenCategory, onChooseMonth = { choosingMonth = true })
        }
    }
    if (model != null && choosingMonth) {
        MonthPickerSheet(model, onPick = { onRangeChange(ReportRange.Month(it)); choosingMonth = false }, onDismiss = { choosingMonth = false })
    }
    if (model != null && exporting) {
        ExportSheet(
            model = model,
            state = export,
            onExport = { span -> onExport(span, model.month, model.earliestMonth) },
            onDismiss = {
                exporting = false
                onExportDismissed()
            },
        )
    }
}

@Composable
private fun ReportsContent(
    model: ReportsModel,
    metric: ReportMetric,
    onRangeChange: (ReportRange) -> Unit,
    onGranularityChange: (TrendGranularity) -> Unit,
    onMetricChange: (ReportMetric) -> Unit,
    onOpenCategory: (CategoryId, YearMonth) -> Unit,
    onChooseMonth: () -> Unit,
) {
    val spacing = ArcaDesignSystem.spacing
    val colors = ArcaDesignSystem.colors
    val range = model.selection.range
    val choice = when (range) {
        ReportRange.LastDays(7) -> RangeChoice.WEEK
        is ReportRange.Month -> RangeChoice.MONTH
        else -> RangeChoice.FOUR_WEEKS
    }
    // What's measured: spending, money received, or the two against each other.
    Row(Modifier.padding(top = spacing.space4), horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
        listOf(
            ReportMetric.EXPENSES to R.string.reports_metric_expenses,
            ReportMetric.INCOME to R.string.reports_metric_income,
            ReportMetric.NET to R.string.reports_metric_net,
        ).forEach { (value, label) ->
            TextChip(stringResource(label), selected = metric == value, onClick = { onMetricChange(value) }, large = true)
        }
    }
    SegmentedControl(
        options = listOf(
            RangeChoice.WEEK to stringResource(R.string.reports_range_7),
            RangeChoice.FOUR_WEEKS to stringResource(R.string.reports_range_28),
            RangeChoice.MONTH to stringResource(R.string.reports_range_month),
        ),
        selected = choice,
        onSelect = {
            onRangeChange(
                when (it) {
                    RangeChoice.WEEK -> ReportRange.LastDays(7)
                    RangeChoice.FOUR_WEEKS -> ReportRange.LastDays(28)
                    RangeChoice.MONTH -> ReportRange.Month(model.currentMonth)
                },
            )
        },
        modifier = Modifier.padding(top = spacing.space4),
    )

    // Period line: a month navigator for Monthly, the date span otherwise.
    Row(
        Modifier.padding(top = spacing.space4, bottom = spacing.space5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val month = (range as? ReportRange.Month)?.month
        val navAlpha = if (month == null) 0f else 1f
        IconAction(
            ArcaIcons.chevronLeft,
            stringResource(R.string.action_previous_month),
            { if (month != null) onRangeChange(ReportRange.Month(month.minusMonths(1))) },
            size = 32.dp,
            enabled = month != null && (model.earliestMonth == null || month > model.earliestMonth!!),
            modifier = Modifier
                .offset(x = (-8).dp)
                .alpha(navAlpha),
        )
        Row(
            Modifier
                .weight(1f)
                .then(if (month != null) Modifier.pressable(onChooseMonth, onClickLabel = stringResource(R.string.reports_choose_month)) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(periodLabel(model), style = ArcaDesignSystem.typography.headline, color = colors.ink)
            if (month != null) ArcaIcon(ArcaIcons.chevronDown, tint = colors.inkTertiary, size = 16.dp)
        }
        IconAction(
            ArcaIcons.chevronRight,
            stringResource(R.string.action_next_month),
            { if (month != null) onRangeChange(ReportRange.Month(month.plusMonths(1))) },
            size = 32.dp,
            enabled = month != null && month < model.currentMonth,
            modifier = Modifier
                .offset(x = 8.dp)
                .alpha(navAlpha),
        )
    }

    // Total
    val total = model.total(metric)
    SectionLabel(
        stringResource(
            when (metric) {
                ReportMetric.EXPENSES -> R.string.reports_total_expenses
                ReportMetric.INCOME -> R.string.reports_total_income
                ReportMetric.NET -> R.string.reports_total_net
            },
        ),
        Modifier.padding(bottom = spacing.space3),
    )
    MoneyFigure(
        total.totalMinor,
        rolling = true,
        color = if (metric == ReportMetric.NET && total.totalMinor < 0) colors.negative else colors.ink,
    )
    if (metric == ReportMetric.NET) {
        NetChangePill(total.changeMinor, hasPrevious = model.income.previousMinor > 0 || model.expenses.previousMinor > 0, against = against(model, short = false), modifier = Modifier.padding(top = spacing.space3))
    } else {
        ChangePill(
            percent = total.changePercent,
            hasPrevious = total.previousMinor > 0,
            against = against(model, short = false),
            modifier = Modifier.padding(top = spacing.space3),
        )
    }

    Trend(model, metric, onGranularityChange)

    if (metric == ReportMetric.NET) {
        // The net has no categories of its own: money in and money out, side by side.
        SectionLabel(stringResource(R.string.reports_in_and_out), Modifier.padding(top = spacing.space7, bottom = spacing.space5))
        InOutRow(stringResource(R.string.reports_metric_income), model.income, model.incomeShare, colors.income, 0, model.selection, against(model, short = true))
        InOutRow(
            stringResource(R.string.reports_metric_expenses),
            model.expenses,
            model.expenseShare,
            colors.ink,
            1,
            model.selection,
            against(model, short = true),
            Modifier.padding(top = spacing.space4),
        )
        return
    }

    // Categories: the four largest, then "Everything else".
    val categories = model.categories(metric)
    if (categories.isNotEmpty()) {
        SectionLabel(
            stringResource(if (metric == ReportMetric.INCOME) R.string.reports_income_by_category else R.string.summary_by_category),
            Modifier.padding(top = spacing.space7, bottom = spacing.space5),
        )
        categories.forEachIndexed { index, category ->
            CategoryRow(
                category = category,
                index = index,
                against = against(model, short = true),
                replayKey = model.selection to metric,
                onClick = { category.categoryId?.let { onOpenCategory(it, model.month) } },
                modifier = Modifier.padding(bottom = if (index == categories.lastIndex) 0.dp else spacing.space4),
            )
        }
    } else {
        Text(
            stringResource(if (metric == ReportMetric.INCOME) R.string.reports_no_income else R.string.reports_no_expenses),
            style = ArcaDesignSystem.typography.subhead,
            color = colors.inkSecondary,
            modifier = Modifier.padding(top = spacing.space7),
        )
    }
}

/** The net view's rows: money in, money out, each against the larger, with its change. */
@Composable
private fun InOutRow(
    label: String,
    total: MetricTotal,
    share: Float,
    color: Color,
    index: Int,
    replayKey: Any,
    against: String,
    modifier: Modifier = Modifier,
) {
    val colors = ArcaDesignSystem.colors
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = ArcaDesignSystem.typography.headline, color = colors.ink, modifier = Modifier.weight(1f))
            RollingText(ArcaDesignSystem.money.digits(total.totalMinor), ArcaDesignSystem.typography.amountRow, colors.inkSecondary)
        }
        ProgressBar(
            share,
            color,
            Modifier.padding(top = 7.dp),
            delayMillis = 200 + index * ArcaDesignSystem.motion.revealStepMillis,
            replayKey = replayKey,
        )
        ChangeChip(total.changePercent, against)
    }
}

/** "▲ +Rp200,000 vs September": the net's change, in money, since a net can cross zero. */
@Composable
private fun NetChangePill(changeMinor: Long, hasPrevious: Boolean, against: String, modifier: Modifier = Modifier) {
    val colors = ArcaDesignSystem.colors
    Row(
        modifier
            .background(colors.surfaceSunken, ArcaDesignSystem.shapes.pill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        when {
            !hasPrevious -> Text(stringResource(R.string.reports_first_on_record), style = ArcaDesignSystem.typography.footnote, color = colors.inkSecondary)
            changeMinor == 0L -> Text(stringResource(R.string.reports_same_as_short, against), style = ArcaDesignSystem.typography.footnote, color = colors.inkSecondary)
            else -> {
                ChangeCaret(up = changeMinor > 0, color = colors.ink)
                Text(ArcaDesignSystem.money.signed(changeMinor), style = ArcaDesignSystem.typography.footnote, color = colors.ink)
                Text(stringResource(R.string.reports_vs, against), style = ArcaDesignSystem.typography.footnote, color = colors.inkSecondary)
            }
        }
    }
}

/** "Sep 3 – 30, 2026" for a rolling range; "September 2026" for a month. */
@Composable
private fun periodLabel(model: ReportsModel): String {
    val locale = appLocale()
    val range = model.selection.range
    if (range is ReportRange.Month) {
        return range.month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + range.month.year
    }
    val first = model.firstDay
    val last = model.lastDay
    val firstText = first.month.getDisplayName(TextStyle.SHORT, locale) + " " + first.dayOfMonth
    val lastText = (if (first.month != last.month) last.month.getDisplayName(TextStyle.SHORT, locale) + " " else "") + last.dayOfMonth
    return "$firstText – $lastText, ${last.year}"
}

/** "August" for a month; "previous 28 days" (or just "previous", in a tight chip) otherwise. */
@Composable
private fun against(model: ReportsModel, short: Boolean): String = when (val range = model.selection.range) {
    is ReportRange.Month -> range.month.minusMonths(1).month.getDisplayName(TextStyle.FULL_STANDALONE, appLocale())
    is ReportRange.LastDays -> if (short) stringResource(R.string.reports_previous) else pluralStringResource(R.plurals.reports_previous_days, range.days, range.days)
}

/**
 * "▾ 15% vs previous 28 days": the change against the equal range before. "First month on
 * record" with nothing to compare; "Same as August" with no change.
 */
@Composable
private fun ChangePill(percent: Int?, hasPrevious: Boolean, against: String, modifier: Modifier = Modifier) {
    val colors = ArcaDesignSystem.colors
    Row(
        modifier
            .background(colors.surfaceSunken, ArcaDesignSystem.shapes.pill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        when {
            !hasPrevious || percent == null -> Text(stringResource(R.string.reports_first_on_record), style = ArcaDesignSystem.typography.footnote, color = colors.inkSecondary)
            percent == 0 -> Text(stringResource(R.string.reports_same_as_short, against), style = ArcaDesignSystem.typography.footnote, color = colors.inkSecondary)
            else -> {
                ChangeCaret(up = percent > 0, color = colors.ink)
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingText(kotlin.math.abs(percent).toString(), ArcaDesignSystem.typography.footnote, colors.ink)
                    Text("%", style = ArcaDesignSystem.typography.footnote, color = colors.ink)
                    Text(" " + stringResource(R.string.reports_vs, against), style = ArcaDesignSystem.typography.footnote, color = colors.inkSecondary)
                }
            }
        }
    }
}

/**
 * The trend: Daily / Weekly, a readout of the selected bar with steppers, then the columns. A
 * finger on the columns reads out the bar under it as it moves.
 */
@Composable
private fun Trend(model: ReportsModel, metric: ReportMetric, onGranularityChange: (TrendGranularity) -> Unit) {
    val spacing = ArcaDesignSystem.spacing
    val colors = ArcaDesignSystem.colors
    val locale = appLocale()
    val bars = model.trend
    var selected by remember(model.selection, bars.size) { mutableIntStateOf(bars.lastIndex) }
    var hovered by remember(model.selection, bars.size) { mutableStateOf<Int?>(null) }
    Row(
        Modifier
            .padding(top = spacing.space7)
            .heightIn(min = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(
            stringResource(
                when (metric) {
                    ReportMetric.EXPENSES -> R.string.reports_trend
                    ReportMetric.INCOME -> R.string.reports_trend_income
                    ReportMetric.NET -> R.string.reports_trend_net
                },
            ),
            Modifier.weight(1f),
        )
        if (model.selection.range != ReportRange.LastDays(7)) {
            SegmentedControl(
                options = listOf(
                    TrendGranularity.DAILY to stringResource(R.string.reports_daily),
                    TrendGranularity.WEEKLY to stringResource(R.string.reports_weekly),
                ),
                selected = model.granularity,
                onSelect = onGranularityChange,
                compact = true,
                modifier = Modifier.width(168.dp),
            )
        }
    }
    if (bars.isEmpty()) return
    val shown = (hovered ?: selected).coerceIn(0, bars.lastIndex)
    val pick = bars[shown]
    val daily = model.granularity == TrendGranularity.DAILY
    fun span(first: LocalDate, last: LocalDate): String {
        val a = first.month.getDisplayName(TextStyle.SHORT, locale) + " " + first.dayOfMonth
        val b = (if (first.month != last.month) last.month.getDisplayName(TextStyle.SHORT, locale) + " " else "") + last.dayOfMonth
        return "$a–$b"
    }
    val pickName = if (daily) {
        pick.first.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) + ", " + pick.first.month.getDisplayName(TextStyle.SHORT, locale) + " " + pick.first.dayOfMonth
    } else {
        stringResource(R.string.reports_week_n, shown + 1) + " · " + span(pick.first, pick.last) +
            if (pick.dayCount != 7) " " + pluralStringResource(R.plurals.reports_n_days, pick.dayCount, pick.dayCount) else ""
    }
    Row(Modifier.padding(vertical = spacing.space3), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            val strong = ArcaDesignSystem.typography.footnote.copy(fontWeight = FontWeight.Medium)
            val value = pick.valueOf(metric)
            val ink = if (value < 0) colors.negative else colors.ink
            Text("Rp", style = strong, color = ink)
            RollingText(ArcaDesignSystem.money.digits(value), strong, ink)
            Text(" · $pickName", style = ArcaDesignSystem.typography.footnote, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.offset(x = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconAction(ArcaIcons.chevronLeft, stringResource(R.string.reports_previous_bar), { if (selected > 0) selected-- }, enabled = selected > 0)
            IconAction(ArcaIcons.chevronRight, stringResource(R.string.reports_next_bar), { if (selected < bars.lastIndex) selected++ }, enabled = selected < bars.lastIndex)
        }
    }
    val sevenDays = model.selection.range == ReportRange.LastDays(7)
    BarChart(
        fractions = bars.map { it.fractionOf(metric) },
        labels = bars.mapIndexed { i, bar ->
            when {
                !daily -> span(bar.first, bar.last)
                sevenDays -> bar.first.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                i % 7 == 0 -> bar.first.dayOfMonth.toString()
                else -> ""
            }
        },
        spoken = bars.map { bar ->
            (if (daily) bar.first.toString() else span(bar.first, bar.last)) + ", " + ArcaDesignSystem.spokenMoney.format(bar.valueOf(metric))
        },
        selected = selected,
        onSelect = { if (it != null) selected = it },
        onHover = { hovered = it },
        replayKey = Triple(model.selection, model.granularity, metric),
        colors = when (metric) {
            ReportMetric.INCOME -> BarColors(colors.income.copy(alpha = 0.4f), colors.income, colors.negative.copy(alpha = 0.45f), colors.negative)
            else -> neutralBarColors()
        },
        maxBarWidth = if (bars.size == 7) 30.dp else 40.dp,
    )
}

/** One category: name, the amount (rolling), its share of the total, and the change chip. */
@Composable
private fun CategoryRow(category: ReportCategory, index: Int, against: String, replayKey: Any, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ArcaDesignSystem.colors
    Column(
        modifier
            .fillMaxWidth()
            .pressable(onClick, pressedScale = 1f, enabled = category.categoryId != null, disabledAlpha = 1f),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                category.name ?: stringResource(R.string.reports_everything_else),
                style = ArcaDesignSystem.typography.headline,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            RollingText(ArcaDesignSystem.money.digits(category.totalMinor), ArcaDesignSystem.typography.amountRow, colors.inkSecondary)
        }
        ProgressBar(
            category.share,
            category.colorHex?.let(colors::category) ?: colors.inkTertiary,
            Modifier.padding(top = 7.dp),
            delayMillis = 200 + index * ArcaDesignSystem.motion.revealStepMillis,
            replayKey = replayKey,
        )
        ChangeChip(category.changePercent, against)
    }
}

/** "▾ 12% vs previous" under a bar; "No change" at 0; nothing with nothing to compare. */
@Composable
private fun ChangeChip(change: Int?, against: String) {
    val colors = ArcaDesignSystem.colors
    when {
        change == null -> Unit
        change == 0 -> Text(
            stringResource(R.string.reports_no_change),
            style = ArcaDesignSystem.typography.footnote,
            color = colors.inkTertiary,
            modifier = Modifier.padding(top = 6.dp),
        )
        else -> Row(
            Modifier
                .padding(top = 6.dp)
                .background(colors.surfaceSunken, ArcaDesignSystem.shapes.pill)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ChangeCaret(up = change > 0, color = colors.inkSecondary, size = 8.dp)
            Text(
                kotlin.math.abs(change).toString() + "% " + stringResource(R.string.reports_vs, against),
                style = ArcaDesignSystem.typography.footnote,
                color = colors.inkSecondary,
            )
        }
    }
}

/** Choose Month: a year switch and twelve months; months with no data yet are dimmed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthPickerSheet(model: ReportsModel, onPick: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    val colors = ArcaDesignSystem.colors
    val spacing = ArcaDesignSystem.spacing
    val locale = appLocale()
    val shown = model.month
    val earliest = model.earliestMonth ?: model.currentMonth
    var year by remember { mutableIntStateOf(shown.year) }
    ArcaSheet(onDismiss) {
        Row(Modifier.padding(start = spacing.space5, end = spacing.space5 - 8.dp, top = spacing.space4), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reports_choose_month), style = ArcaDesignSystem.typography.headline, color = colors.inkSecondary, modifier = Modifier.weight(1f))
            IconAction(ArcaIcons.chevronLeft, stringResource(R.string.reports_previous_year), { year-- }, enabled = year > earliest.year)
            Text(year.toString(), style = ArcaDesignSystem.typography.headline, color = colors.ink, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
            IconAction(ArcaIcons.chevronRight, stringResource(R.string.reports_next_year), { year++ }, enabled = year < model.currentMonth.year)
        }
        Column(Modifier.padding(start = spacing.space5, end = spacing.space5, top = spacing.space5), verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
            (1..12).chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                    row.forEach { m ->
                        val month = YearMonth.of(year, m)
                        val available = month in earliest..model.currentMonth
                        val isSelected = month == shown && model.selection.range is ReportRange.Month
                        val fill by animateColorAsState(
                            when {
                                isSelected -> colors.accent
                                available -> colors.surfaceSunken
                                else -> Color.Transparent
                            },
                            tween(ArcaDesignSystem.motion.stateMillis),
                            label = "monthWell",
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .background(fill, ArcaDesignSystem.shapes.medium)
                                .alpha(if (available || isSelected) 1f else 0.45f)
                                .pressable({ if (available) onPick(month) }, pressedScale = 0.94f, enabled = available, disabledAlpha = 1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                month.month.getDisplayName(TextStyle.SHORT, locale),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = when {
                                    isSelected -> colors.accentOn
                                    available -> colors.ink
                                    else -> colors.inkTertiary
                                },
                            )
                        }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.reports_data_since, earliest.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + earliest.year),
            style = ArcaDesignSystem.typography.footnote,
            color = colors.inkTertiary,
            modifier = Modifier.padding(start = spacing.space5, end = spacing.space5, top = spacing.space4, bottom = spacing.space6),
        )
    }
}

/** Export CSV: three spans, what the file holds, then Export; afterwards, what was saved. */
@Composable
private fun ExportSheet(model: ReportsModel, state: ExportState, onExport: (ExportSpan) -> Unit, onDismiss: () -> Unit) {
    val colors = ArcaDesignSystem.colors
    val spacing = ArcaDesignSystem.spacing
    val locale = appLocale()
    var span by rememberSaveable { mutableStateOf(ExportSpan.THIS_MONTH) }
    fun label(month: YearMonth) = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + month.year
    ArcaSheet(onDismiss) {
        if (state is ExportState.Done) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = spacing.space5, end = spacing.space5, top = spacing.space6, bottom = spacing.space6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(56.dp).background(colors.surfaceSunken, ArcaDesignSystem.shapes.pill), contentAlignment = Alignment.Center) {
                    ArcaIcon(ArcaIcons.check, tint = colors.ink)
                }
                Text(stringResource(R.string.reports_exported), style = ArcaDesignSystem.typography.title, color = colors.ink, modifier = Modifier.padding(top = spacing.space4, bottom = spacing.space2))
                Text(
                    pluralStringResource(R.plurals.reports_exported_count, state.result.count, state.result.count),
                    style = ArcaDesignSystem.typography.body,
                    color = colors.inkSecondary,
                    textAlign = TextAlign.Center,
                )
                Text(state.result.fileName, style = ArcaDesignSystem.typography.footnote, color = colors.inkTertiary, modifier = Modifier.padding(top = spacing.space2))
                Box(
                    Modifier
                        .padding(top = spacing.space6)
                        .fillMaxWidth()
                        .background(colors.surfaceSunken, ArcaDesignSystem.shapes.pill)
                        .pressable(onDismiss)
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.action_done), style = ArcaDesignSystem.typography.headline, color = colors.ink)
                }
            }
            return@ArcaSheet
        }
        Row(Modifier.padding(start = spacing.space5, end = spacing.space5 - 8.dp, top = spacing.space4), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reports_export), style = ArcaDesignSystem.typography.headline, color = colors.inkSecondary, modifier = Modifier.weight(1f))
            IconAction(ArcaIcons.close, stringResource(R.string.action_close), onDismiss, size = 32.dp)
        }
        val month = model.month
        val earliest = model.earliestMonth ?: month
        Column(Modifier.padding(start = spacing.space5, end = spacing.space5, top = spacing.space4)) {
            listOf(
                Triple(ExportSpan.THIS_MONTH, stringResource(R.string.reports_export_this_month), label(month)),
                Triple(ExportSpan.LAST_3_MONTHS, stringResource(R.string.reports_export_3_months), label(month.minusMonths(2)) + " – " + label(month)),
                Triple(ExportSpan.ALL, stringResource(R.string.reports_export_all), label(earliest) + " – " + label(month)),
            ).forEachIndexed { i, (value, title, subtitle) ->
                if (i > 0) Hairline(startInset = 34.dp)
                RadioRow(title, subtitle, selected = span == value, onClick = { span = value })
            }
        }
        Text(
            stringResource(R.string.reports_export_help),
            style = ArcaDesignSystem.typography.footnote,
            color = colors.inkTertiary,
            modifier = Modifier.padding(start = spacing.space5, end = spacing.space5, top = spacing.space3),
        )
        if (state is ExportState.Failed) {
            Text(
                stringResource(R.string.reports_export_failed),
                style = ArcaDesignSystem.typography.footnote,
                color = colors.negative,
                modifier = Modifier.padding(start = spacing.space5, end = spacing.space5, top = spacing.space2),
            )
        }
        PrimaryButton(
            stringResource(R.string.reports_export_action),
            { onExport(span) },
            Modifier.padding(start = spacing.space5, end = spacing.space5, top = spacing.space5, bottom = spacing.space6),
            enabled = state != ExportState.Working,
        )
    }
}

/** A radio row: a 22dp ring that fills with the accent, a title and a line under it. */
@Composable
fun RadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ArcaDesignSystem.colors
    val motion = ArcaDesignSystem.motion
    val ring by animateColorAsState(if (selected) colors.accent else colors.hairline, tween(motion.stateMillis), label = "ring")
    val fill by animateColorAsState(if (selected) colors.accent else Color.Transparent, tween(motion.stateMillis), label = "fill")
    Row(
        modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .pressable(onClick, pressedScale = 1f, role = Role.RadioButton)
            .padding(vertical = ArcaDesignSystem.spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space3),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .background(fill, ArcaDesignSystem.shapes.pill)
                .border(1.5.dp, ring, ArcaDesignSystem.shapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(8.dp).background(colors.accentOn, ArcaDesignSystem.shapes.pill))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = ArcaDesignSystem.typography.headline, color = colors.ink)
            if (subtitle != null) Text(subtitle, style = ArcaDesignSystem.typography.footnote, color = colors.inkTertiary, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

/**
 * The prototype's bottom sheet: raised surface, 28dp top corners, a 36 × 4 handle, the scrim at
 * rgba(12,10,8,0.38). Rises in 420ms on the drawer curve.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcaSheet(onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val colors = ArcaDesignSystem.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ArcaDesignSystem.shapes.sheetTop,
        containerColor = colors.surfaceRaised,
        scrimColor = Color(0x610C0A08),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp).size(width = 36.dp, height = 4.dp).background(colors.hairline, ArcaDesignSystem.shapes.pill))
        },
    ) {
        Column(Modifier.navigationBarsPadding(), content = content)
    }
}
