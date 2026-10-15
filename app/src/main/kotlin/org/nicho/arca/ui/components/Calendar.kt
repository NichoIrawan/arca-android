package org.nicho.arca.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.feature.reports.ArcaSheet
import org.nicho.arca.ui.format.appLocale

/**
 * A month of days as the prototype draws them (boards 14 and 15): weeks start on Monday, each
 * day a 40dp circle in a 44dp cell. The chosen day is filled with the accent; today has a thin
 * ring; days after [latest] are faded and can't be picked. A range is a sunken band from
 * [rangeStart] to [rangeEnd] with both ends filled.
 */
@Composable
fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    selected: LocalDate? = null,
    rangeStart: LocalDate? = null,
    rangeEnd: LocalDate? = null,
    latest: LocalDate? = today,
) {
    val colors = ArcaDesignSystem.colors
    val locale = appLocale()
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = ArcaDesignSystem.spacing.space1)) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.SHORT, locale).uppercase(locale),
                    style = ArcaDesignSystem.typography.label,
                    color = colors.inkTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val offset = month.atDay(1).dayOfWeek.value - 1 // Monday = 0
        val length = month.lengthOfMonth()
        val weeks = (offset + length + 6) / 7
        val fullFormat = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale)
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNumber = w * 7 + c - offset + 1
                    if (dayNumber !in 1..length) {
                        Box(Modifier.weight(1f).height(44.dp))
                        continue
                    }
                    val date = month.atDay(dayNumber)
                    val future = latest != null && date.isAfter(latest)
                    val isEnd = date == selected || date == rangeStart || date == rangeEnd
                    val inBand = rangeStart != null && rangeEnd != null && rangeStart != rangeEnd && !date.isBefore(rangeStart) && !date.isAfter(rangeEnd)
                    val fill by animateColorAsState(if (isEnd) colors.accent else Color.Transparent, tween(ArcaDesignSystem.motion.stateMillis), label = "day")
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .semantics {
                                contentDescription = date.format(fullFormat)
                                this.selected = isEnd
                            }
                            .pressable({ if (!future) onPick(date) }, enabled = !future, pressedScale = 0.94f, disabledAlpha = 1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (inBand) {
                            // The band runs cell to cell, rounded only where the range starts and ends.
                            val start = date == rangeStart || c == 0
                            val end = date == rangeEnd || c == 6
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .background(
                                        colors.surfaceSunken,
                                        RoundedCornerShape(
                                            topStart = if (start) 20.dp else 0.dp,
                                            bottomStart = if (start) 20.dp else 0.dp,
                                            topEnd = if (end) 20.dp else 0.dp,
                                            bottomEnd = if (end) 20.dp else 0.dp,
                                        ),
                                    ),
                            )
                        }
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(fill, ArcaDesignSystem.shapes.pill)
                                .then(if (date == today && !isEnd) Modifier.border(1.dp, colors.inkTertiary, ArcaDesignSystem.shapes.pill) else Modifier)
                                .alpha(if (future) 0.5f else 1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                dayNumber.toString(),
                                style = ArcaDesignSystem.typography.subhead.copy(fontFeatureSettings = "tnum"),
                                color = when {
                                    isEnd -> colors.accentOn
                                    future -> colors.inkTertiary
                                    else -> colors.ink
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "September 2026 ⌄            ‹ ›": the month bar over a grid. */
@Composable
private fun MonthBar(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    latest: YearMonth?,
    modifier: Modifier = Modifier,
    pickerOpen: Boolean? = null,
    onTogglePicker: (() -> Unit)? = null,
) {
    val colors = ArcaDesignSystem.colors
    val locale = appLocale()
    val turn by animateFloatAsState(if (pickerOpen == true) 180f else 0f, tween(ArcaDesignSystem.motion.stateMillis, easing = ArcaDesignSystem.motion.easeOut), label = "chevron")
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .then(if (onTogglePicker != null) Modifier.pressable(onTogglePicker) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space1),
        ) {
            Text(month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + month.year, style = ArcaDesignSystem.typography.headline, color = colors.ink)
            if (onTogglePicker != null) ArcaIcon(ArcaIcons.chevronDown, tint = colors.inkTertiary, size = 16.dp, modifier = Modifier.rotate(turn))
        }
        if (pickerOpen != true) {
            Row(Modifier.padding(end = 0.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconAction(ArcaIcons.chevronLeft, stringResource(R.string.action_previous_month), { onMonth(month.minusMonths(1)) })
                IconAction(
                    ArcaIcons.chevronRight,
                    stringResource(R.string.action_next_month),
                    { onMonth(month.plusMonths(1)) },
                    enabled = latest == null || month < latest,
                )
            }
        }
    }
}

/** The sheets' shared head: ✕ on the left, the title centred in ink-secondary. */
@Composable
fun SheetTitleBar(title: String, onClose: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Box(modifier.fillMaxWidth().padding(start = ArcaDesignSystem.spacing.space5 - 8.dp, end = ArcaDesignSystem.spacing.space5, top = ArcaDesignSystem.spacing.space4)) {
        IconAction(ArcaIcons.close, stringResource(R.string.action_close), onClose, size = 32.dp, modifier = Modifier.align(Alignment.CenterStart))
        Text(title, style = ArcaDesignSystem.typography.headline, color = ArcaDesignSystem.colors.inkSecondary, modifier = Modifier.align(Alignment.Center))
        if (trailing != null) Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

/** A sheet's footer: a rule, the primary action, and a quiet Cancel under it. */
@Composable
fun SheetFooter(action: String, onAction: () -> Unit, onCancel: () -> Unit, enabled: Boolean = true) {
    Hairline()
    PrimaryButton(
        action,
        onAction,
        Modifier.padding(start = ArcaDesignSystem.spacing.space5, end = ArcaDesignSystem.spacing.space5, top = ArcaDesignSystem.spacing.space4),
        enabled = enabled,
    )
    Text(
        stringResource(R.string.action_cancel),
        style = ArcaDesignSystem.typography.body,
        color = ArcaDesignSystem.colors.inkSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onCancel)
            .padding(vertical = ArcaDesignSystem.spacing.space4),
    )
}

/**
 * Pick a date (board 14): the chosen day spelled out, Today / Yesterday / 2 days ago, then the
 * calendar (or, from the month title, a grid of months). Future days can't be picked (FR-101).
 */
@Composable
fun DatePickerSheet(initial: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val colors = ArcaDesignSystem.colors
    val spacing = ArcaDesignSystem.spacing
    val locale = appLocale()
    var chosen by rememberSaveable { mutableStateOf(initial) }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(initial)) }
    var pickingMonth by rememberSaveable { mutableStateOf(false) }
    val quick = listOf(today to R.string.date_today, today.minusDays(1) to R.string.date_yesterday, today.minusDays(2) to R.string.date_two_days_ago)
    ArcaSheet(onDismiss) {
        SheetTitleBar(stringResource(R.string.date_pick_title), onDismiss)
        Column(Modifier.padding(horizontal = spacing.space5).weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            val relative = quick.firstOrNull { it.first == chosen }?.second ?: R.string.date_selected
            Text(stringResource(relative).uppercase(locale), style = ArcaDesignSystem.typography.label, color = colors.inkSecondary, modifier = Modifier.padding(top = spacing.space5, bottom = spacing.space2))
            Text(
                chosen.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)),
                style = ArcaDesignSystem.typography.title.copy(fontSize = 24.sp, lineHeight = 30.sp),
                color = colors.ink,
                maxLines = 1,
            )
            Row(Modifier.padding(top = spacing.space4), horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                quick.forEach { (date, label) ->
                    TextChip(stringResource(label), selected = chosen == date, onClick = { chosen = date; month = YearMonth.from(date) }, large = true)
                }
            }
            MonthBar(
                month,
                { month = it },
                latest = YearMonth.from(today),
                modifier = Modifier.padding(top = spacing.space5),
                pickerOpen = pickingMonth,
                onTogglePicker = { pickingMonth = !pickingMonth },
            )
            Box(Modifier.padding(top = spacing.space3).height(300.dp)) {
                if (pickingMonth) {
                    MonthChoice(month.year, month, YearMonth.from(today)) {
                        month = it
                        pickingMonth = false
                    }
                } else {
                    MonthGrid(month, today, onPick = { chosen = it }, selected = chosen)
                }
            }
        }
        SheetFooter(stringResource(R.string.date_select), { onPick(chosen) }, onDismiss)
        Spacer(Modifier.height(spacing.space5))
    }
}

/** Twelve month wells, three to a row; months after [latest] fade. */
@Composable
private fun MonthChoice(year: Int, current: YearMonth, latest: YearMonth, onPick: (YearMonth) -> Unit) {
    val colors = ArcaDesignSystem.colors
    val locale = appLocale()
    Column(verticalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space2), modifier = Modifier.padding(top = ArcaDesignSystem.spacing.space2)) {
        (1..12).chunked(3).forEach { row ->
            Row {
                row.forEach { m ->
                    val month = YearMonth.of(year, m)
                    val isCurrent = month == current
                    val allowed = month <= latest
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .height(48.dp)
                            .background(if (isCurrent) colors.accent else colors.surfaceSunken, ArcaDesignSystem.shapes.medium)
                            .alpha(if (allowed) 1f else 0.3f)
                            .pressable({ if (allowed) onPick(month) }, enabled = allowed, pressedScale = 0.94f, disabledAlpha = 0.3f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            month.month.getDisplayName(TextStyle.SHORT, locale),
                            style = ArcaDesignSystem.typography.subhead,
                            color = if (isCurrent) colors.accentOn else colors.inkSecondary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Date range (board 15): From → To with the day count, four presets, and a calendar where the
 * first tap starts a range and the second ends it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DateRangeSheet(initialFrom: LocalDate?, initialTo: LocalDate?, today: LocalDate, onApply: (LocalDate, LocalDate) -> Unit, onDismiss: () -> Unit) {
    val colors = ArcaDesignSystem.colors
    val spacing = ArcaDesignSystem.spacing
    val locale = appLocale()
    var from by rememberSaveable { mutableStateOf(initialFrom) }
    var to by rememberSaveable { mutableStateOf(initialTo) }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(initialTo ?: today)) }
    val format = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)
    val quarterStart = today.withMonth(((today.monthValue - 1) / 3) * 3 + 1).withDayOfMonth(1)
    val presets = listOf(
        Triple(R.string.range_last_7_days, today.minusDays(6), today),
        Triple(R.string.filter_this_month, today.withDayOfMonth(1), today),
        Triple(R.string.filter_last_month, YearMonth.from(today).minusMonths(1).atDay(1), YearMonth.from(today).minusMonths(1).atEndOfMonth()),
        Triple(R.string.range_this_quarter, quarterStart, today),
    )
    ArcaSheet(onDismiss) {
        SheetTitleBar(stringResource(R.string.range_title), onDismiss)
        Column(Modifier.padding(horizontal = spacing.space5).weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(top = spacing.space5, bottom = spacing.space2), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.range_label), Modifier.weight(1f))
                val a = from
                val b = to
                Text(
                    if (a != null && b != null) {
                        val days = ChronoUnit.DAYS.between(a, b).toInt() + 1
                        pluralStringResource(R.plurals.range_days, days, days)
                    } else {
                        stringResource(R.string.range_pick_end)
                    },
                    style = ArcaDesignSystem.typography.footnote,
                    color = if (a != null && b != null) colors.ink else colors.inkTertiary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
                RangeField(stringResource(R.string.capture_from), from?.format(format), active = from == null || to != null, Modifier.weight(1f))
                ArcaIcon(ArcaIcons.arrowRight, tint = colors.inkTertiary, size = 18.dp)
                RangeField(stringResource(R.string.capture_to), to?.format(format), active = from != null && to == null, Modifier.weight(1f))
            }
            FlowRow(Modifier.padding(top = spacing.space4), horizontalArrangement = Arrangement.spacedBy(spacing.space2), verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                presets.forEach { (label, a, b) ->
                    TextChip(stringResource(label), selected = from == a && to == b, onClick = { from = a; to = b; month = YearMonth.from(b) }, large = true)
                }
            }
            MonthBar(month, { month = it }, latest = YearMonth.from(today), modifier = Modifier.padding(top = spacing.space5))
            MonthGrid(
                month,
                today,
                onPick = { day ->
                    val a = from
                    when {
                        a == null || to != null -> { from = day; to = null }
                        day.isBefore(a) -> { from = day; to = a }
                        else -> to = day
                    }
                },
                modifier = Modifier.padding(top = spacing.space3, bottom = spacing.space2),
                rangeStart = from,
                rangeEnd = to ?: from,
            )
        }
        val a = from
        val b = to
        SheetFooter(stringResource(R.string.range_apply), { if (a != null) onApply(a, b ?: a) }, onDismiss, enabled = a != null)
    }
}

@Composable
private fun RangeField(label: String, value: String?, active: Boolean, modifier: Modifier = Modifier) {
    val colors = ArcaDesignSystem.colors
    Column(modifier.padding(vertical = ArcaDesignSystem.spacing.space3)) {
        Text(label.uppercase(appLocale()), style = ArcaDesignSystem.typography.label, color = colors.inkTertiary, modifier = Modifier.padding(bottom = ArcaDesignSystem.spacing.space1))
        Text(value ?: "—", style = ArcaDesignSystem.typography.headline, color = if (value == null) colors.inkTertiary else colors.ink, maxLines = 1)
        Box(Modifier.padding(top = ArcaDesignSystem.spacing.space3).fillMaxWidth().height(if (active) 2.dp else 1.dp).background(if (active) colors.accent else colors.hairline))
    }
}
