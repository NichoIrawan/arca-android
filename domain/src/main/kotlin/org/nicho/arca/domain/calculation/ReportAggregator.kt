package org.nicho.arca.domain.calculation

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.CategorySpend
import org.nicho.arca.domain.model.Granularity
import org.nicho.arca.domain.model.Period
import org.nicho.arca.domain.model.PeriodSummary
import org.nicho.arca.domain.model.TimeBucket
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionType

/**
 * SDD-ARC-002 §7.3, DD-21. Report arithmetic. Transfers never count as income or spending:
 * every function filters through TransactionType.reportable (DD-10, SRS §6.3, "the most
 * consequential line in the reporting layer"). A transfer fee is an ordinary expense in Fees,
 * so it *is* spending and shows up under Fees (FR-133).
 *
 * Each function takes the period itself and filters to it, half-open [start, end), so a caller
 * can pass a wider list without double counting.
 */
object ReportAggregator {

    /** FR-301. An empty period is a zeroed summary, never null. */
    fun summary(txs: List<Transaction>, period: Period): PeriodSummary {
        var income = 0L
        var expense = 0L
        for (tx in txs) {
            if (!tx.isReportableIn(period)) continue
            when (tx.type) {
                TransactionType.INCOME -> income = Math.addExact(income, tx.amountMinor)
                TransactionType.EXPENSE -> expense = Math.addExact(expense, tx.amountMinor)
                else -> Unit
            }
        }
        return PeriodSummary(income, expense, Math.subtractExact(income, expense))
    }

    /**
     * FR-302: expenditure by category, largest first, so the chart needs no sorting.
     *
     * [categories] must include archived ones: an archived category's past spending still
     * happened. A transaction whose category isn't in the map is left out.
     */
    fun spendByCategory(
        txs: List<Transaction>,
        categories: Map<CategoryId, Category>,
        period: Period,
    ): List<CategorySpend> = totalsByCategory(txs, categories, period, TransactionType.EXPENSE)

    /** What was received, by income category. Same rules as [spendByCategory]. */
    fun incomeByCategory(
        txs: List<Transaction>,
        categories: Map<CategoryId, Category>,
        period: Period,
    ): List<CategorySpend> = totalsByCategory(txs, categories, period, TransactionType.INCOME)

    /**
     * FR-303: expenditure per day or per month across [period], one bucket for every day or month
     * including empty ones, so a chart's axis has no gaps. Buckets are calendar days or months in
     * [zone].
     */
    fun spendOverTime(
        txs: List<Transaction>,
        period: Period,
        granularity: Granularity,
        zone: ZoneId,
    ): List<TimeBucket> {
        val first = bucketOf(period.start, granularity, zone)
        val last = bucketOf(period.endExclusive.minusNanos(1), granularity, zone)
        val totals = LinkedHashMap<LocalDate, Long>()
        var bucket = first
        while (!bucket.isAfter(last)) {
            totals[bucket] = 0L
            bucket = if (granularity == Granularity.DAY) bucket.plusDays(1) else bucket.plusMonths(1)
        }
        for (tx in txs) {
            if (tx.type != TransactionType.EXPENSE || !tx.date.isIn(period)) continue
            val key = bucketOf(tx.date, granularity, zone)
            totals[key] = Math.addExact(totals.getValue(key), tx.amountMinor)
        }
        return totals.map { (start, total) -> TimeBucket(start, total) }
    }

    private fun totalsByCategory(
        txs: List<Transaction>,
        categories: Map<CategoryId, Category>,
        period: Period,
        type: TransactionType,
    ): List<CategorySpend> {
        val totals = LinkedHashMap<CategoryId, Long>()
        var grandTotal = 0L
        for (tx in txs) {
            if (tx.type != type || !tx.date.isIn(period)) continue
            val categoryId = tx.categoryId ?: continue
            if (categoryId !in categories) continue
            totals[categoryId] = Math.addExact(totals[categoryId] ?: 0L, tx.amountMinor)
            grandTotal = Math.addExact(grandTotal, tx.amountMinor)
        }
        return totals
            .map { (id, total) ->
                val category = categories.getValue(id)
                CategorySpend(
                    categoryId = id,
                    name = category.name,
                    icon = category.icon,
                    colorHex = category.colorHex,
                    totalMinor = total,
                    share = total.toFloat() / grandTotal.toFloat(), // geometry only (DD-12)
                )
            }
            .sortedWith(compareByDescending<CategorySpend> { it.totalMinor }.thenBy { it.name })
    }

    private fun Transaction.isReportableIn(period: Period): Boolean =
        type in TransactionType.reportable && date.isIn(period)

    private fun Instant.isIn(period: Period): Boolean = this >= period.start && this < period.endExclusive

    private fun bucketOf(instant: Instant, granularity: Granularity, zone: ZoneId): LocalDate {
        val day = instant.atZone(zone).toLocalDate()
        return if (granularity == Granularity.DAY) day else day.withDayOfMonth(1)
    }
}
