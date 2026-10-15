package org.nicho.arca.domain.readmodel

import java.time.LocalDate
import java.time.YearMonth
import org.nicho.arca.domain.model.CategoryId

/**
 * Which stretch of time Reports covers, as the prototype's range switch offers it: the last 7 or
 * 28 days ending today, or one calendar month (FR-304).
 */
sealed interface ReportRange {
    data class LastDays(val days: Int) : ReportRange
    data class Month(val month: YearMonth) : ReportRange
}

/** The trend's bars: one per day, or one per week (a month's last "week" runs 7–10 days). */
enum class TrendGranularity { DAILY, WEEKLY }

/**
 * What Reports measures (client request, 2026-10-15): money spent, money received, or the two
 * set against each other. Transfers and adjustments are never any of them (DD-10).
 */
enum class ReportMetric { EXPENSES, INCOME, NET }

data class ReportSelection(
    val range: ReportRange,
    val granularity: TrendGranularity = TrendGranularity.WEEKLY,
)

/**
 * One measure over the range and the equal range before it. [changePercent] is null with nothing
 * to compare, and always null for the net, which can cross zero; [changeMinor] is the plain
 * difference, which is how the net is compared.
 */
data class MetricTotal(
    val totalMinor: Long,
    val previousMinor: Long,
    val changePercent: Int?,
    val changeMinor: Long,
)

/**
 * One trend bar, carrying all three measures so switching between them needs no new query. The
 * fractions are heights against the tallest bar of that measure (geometry only, DD-12);
 * [netFraction] keeps its sign, a loss hanging below the zero line.
 */
data class TrendBar(
    val first: LocalDate,
    val last: LocalDate,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val netMinor: Long,
    val incomeFraction: Float,
    val expenseFraction: Float,
    val netFraction: Float,
) {
    val dayCount: Int get() = (last.toEpochDay() - first.toEpochDay()).toInt() + 1

    fun valueOf(metric: ReportMetric): Long = when (metric) {
        ReportMetric.EXPENSES -> expenseMinor
        ReportMetric.INCOME -> incomeMinor
        ReportMetric.NET -> netMinor
    }

    fun fractionOf(metric: ReportMetric): Float = when (metric) {
        ReportMetric.EXPENSES -> expenseFraction
        ReportMetric.INCOME -> incomeFraction
        ReportMetric.NET -> netFraction
    }
}

/**
 * One category bar. [categoryId] is null for the remainder ("Everything else"), whose
 * [restIds] are the categories folded into it. [changePercent] compares with the equal range
 * before; null when that range had nothing in this category.
 */
data class ReportCategory(
    val categoryId: CategoryId?,
    val name: String?,
    val colorHex: String?,
    val totalMinor: Long,
    val share: Float,
    val changePercent: Int?,
    val restIds: List<CategoryId> = emptyList(),
)

/** What Reports (Laporan) shows for one range (FR-301–306), for every measure at once. */
data class ReportsModel(
    val selection: ReportSelection,
    val granularity: TrendGranularity,       // what's drawn: 7 days is always daily
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    val expenses: MetricTotal,
    val income: MetricTotal,
    val net: MetricTotal,
    val trend: List<TrendBar>,
    val expenseCategories: List<ReportCategory>, // the four largest, then the rest as one
    val incomeCategories: List<ReportCategory>,
    /** The net view's two bars, money in and money out, each against the larger (geometry). */
    val incomeShare: Float,
    val expenseShare: Float,
    val earliestMonth: YearMonth?,           // "Data available since…"; null before any data
    val currentMonth: YearMonth,
) {
    val month: YearMonth get() = YearMonth.from(lastDay)

    fun total(metric: ReportMetric): MetricTotal = when (metric) {
        ReportMetric.EXPENSES -> expenses
        ReportMetric.INCOME -> income
        ReportMetric.NET -> net
    }

    /** The category breakdown for [metric]; the net has none of its own. */
    fun categories(metric: ReportMetric): List<ReportCategory> = when (metric) {
        ReportMetric.EXPENSES -> expenseCategories
        ReportMetric.INCOME -> incomeCategories
        ReportMetric.NET -> emptyList()
    }
}
