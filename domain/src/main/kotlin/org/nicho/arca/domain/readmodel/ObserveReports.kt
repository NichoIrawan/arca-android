package org.nicho.arca.domain.readmodel

import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import org.nicho.arca.domain.calculation.ReportAggregator
import org.nicho.arca.domain.model.CategorySpend
import org.nicho.arca.domain.model.Period
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.repository.TransactionReading
import org.nicho.arca.domain.time.PeriodClock

/**
 * Reports for whichever range the user picked, compared with the equal range just before it
 * (FR-306): the previous 7 or 28 days, or the previous month. Expenses, income and the net are
 * all computed at once, so switching between them is instant. The ViewModel owns the selection as
 * state and passes it in; a new selection cancels the old one's queries.
 */
class ObserveReports(
    private val transactions: TransactionReading,
    private val categories: CategoryStore,
    private val clock: PeriodClock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(selections: Flow<ReportSelection>): Flow<ReportsModel> =
        combine(selections, clock.currentMonth) { selection, current -> selection to current }
            .flatMapLatest { (selection, current) ->
                val today = clock.now().atZone(clock.zone()).toLocalDate()
                val (first, last) = days(selection.range, today)
                val length = last.toEpochDay() - first.toEpochDay() + 1
                val (prevFirst, prevLast) = when (val range = selection.range) {
                    is ReportRange.Month -> days(ReportRange.Month(range.month.minusMonths(1)), today)
                    is ReportRange.LastDays -> first.minusDays(length) to first.minusDays(1)
                }
                val period = periodOf(first, last)
                val previousPeriod = periodOf(prevFirst, prevLast)
                val granularity = if (selection.range == ReportRange.LastDays(7)) TrendGranularity.DAILY else selection.granularity
                combine(
                    transactions.observeInPeriod(period),
                    transactions.observeInPeriod(previousPeriod),
                    categories.observeAll(includeArchived = true),
                    transactions.observeEarliestDate(),
                ) { txs, previousTxs, allCategories, earliest ->
                    val byId = allCategories.associateBy { it.id }
                    val now = ReportAggregator.summary(txs, period)
                    val before = ReportAggregator.summary(previousTxs, previousPeriod)
                    val buckets = buckets(first, last, granularity, selection.range is ReportRange.Month)
                    val sums = ReportAggregator.summariesInPeriods(txs, buckets.map { (a, b) -> periodOf(a, b) })
                    val incomeShares = shares(sums.map { it.incomeMinor })
                    val expenseShares = shares(sums.map { it.expenseMinor })
                    val netShares = ReportAggregator.signedShares(sums.map { it.netMinor })
                    val larger = maxOf(now.incomeMinor, now.expenseMinor)
                    ReportsModel(
                        selection = selection,
                        granularity = granularity,
                        firstDay = first,
                        lastDay = last,
                        expenses = MetricTotal(
                            now.expenseMinor,
                            before.expenseMinor,
                            ReportAggregator.changePercent(before.expenseMinor, now.expenseMinor),
                            ReportAggregator.difference(before.expenseMinor, now.expenseMinor),
                        ),
                        income = MetricTotal(
                            now.incomeMinor,
                            before.incomeMinor,
                            ReportAggregator.changePercent(before.incomeMinor, now.incomeMinor),
                            ReportAggregator.difference(before.incomeMinor, now.incomeMinor),
                        ),
                        net = MetricTotal(now.netMinor, before.netMinor, null, ReportAggregator.difference(before.netMinor, now.netMinor)),
                        trend = buckets.mapIndexed { i, (a, b) ->
                            TrendBar(
                                first = a,
                                last = b,
                                incomeMinor = sums[i].incomeMinor,
                                expenseMinor = sums[i].expenseMinor,
                                netMinor = sums[i].netMinor,
                                incomeFraction = incomeShares[i],
                                expenseFraction = expenseShares[i],
                                netFraction = netShares[i],
                            )
                        },
                        expenseCategories = ranked(
                            ReportAggregator.spendByCategory(txs, byId, period),
                            ReportAggregator.spendByCategory(previousTxs, byId, previousPeriod),
                        ),
                        incomeCategories = ranked(
                            ReportAggregator.incomeByCategory(txs, byId, period),
                            ReportAggregator.incomeByCategory(previousTxs, byId, previousPeriod),
                        ),
                        incomeShare = if (larger > 0) now.incomeMinor.toFloat() / larger.toFloat() else 0f, // geometry only
                        expenseShare = if (larger > 0) now.expenseMinor.toFloat() / larger.toFloat() else 0f,
                        earliestMonth = earliest?.let { YearMonth.from(it.atZone(clock.zone())) },
                        currentMonth = current.month,
                    )
                }
            }.flowOn(dispatcher)

    /** The four largest categories with their change, then the rest folded into one. */
    private fun ranked(spend: List<CategorySpend>, previousSpend: List<CategorySpend>): List<ReportCategory> {
        val previousById = previousSpend.associate { it.categoryId to it.totalMinor }
        val ranked = ReportAggregator.topWithRest(spend, keep = 4)
        return ranked.top.map {
            ReportCategory(
                categoryId = it.categoryId,
                name = it.name,
                colorHex = it.colorHex,
                totalMinor = it.totalMinor,
                share = it.share,
                changePercent = ReportAggregator.changePercent(previousById[it.categoryId] ?: 0L, it.totalMinor),
            )
        } + listOfNotNull(
            ranked.rest?.let { rest ->
                ReportCategory(
                    categoryId = null,
                    name = null,
                    colorHex = null,
                    totalMinor = rest.totalMinor,
                    share = rest.share,
                    changePercent = ReportAggregator.changePercent(ReportAggregator.totalFor(previousSpend, rest.categoryIds), rest.totalMinor),
                    restIds = rest.categoryIds,
                )
            },
        )
    }

    /** Heights against the tallest bar (geometry only, DD-12). */
    private fun shares(values: List<Long>): List<Float> {
        val peak = values.maxOrNull() ?: 0L
        return values.map { if (peak > 0) it.toFloat() / peak.toFloat() else 0f }
    }

    /** The first and last day a range covers. A month in progress ends today. */
    private fun days(range: ReportRange, today: LocalDate): Pair<LocalDate, LocalDate> = when (range) {
        is ReportRange.LastDays -> today.minusDays(range.days - 1L) to today
        is ReportRange.Month -> range.month.atDay(1) to range.month.atEndOfMonth()
    }

    /**
     * The trend's buckets. Daily: one per day. Weekly: a month splits 1–7, 8–14, 15–21 and the
     * rest; a rolling range splits into 7-day weeks from its first day.
     */
    private fun buckets(first: LocalDate, last: LocalDate, granularity: TrendGranularity, isMonth: Boolean): List<Pair<LocalDate, LocalDate>> {
        if (granularity == TrendGranularity.DAILY) {
            return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.map { it to it }.toList()
        }
        val starts = generateSequence(first) { it.plusDays(7) }.takeWhile { !it.isAfter(last) }.toList()
        val weeks = starts.map { start -> start to minOf(start.plusDays(6), last) }
        // A month's short tail (29th–31st) joins the fourth week instead of standing alone.
        return if (isMonth && weeks.size > 4) weeks.take(3) + (weeks[3].first to last) else weeks
    }

    private fun periodOf(first: LocalDate, last: LocalDate): Period {
        val zone = clock.zone()
        return Period(first.atStartOfDay(zone).toInstant(), last.plusDays(1).atStartOfDay(zone).toInstant())
    }
}
