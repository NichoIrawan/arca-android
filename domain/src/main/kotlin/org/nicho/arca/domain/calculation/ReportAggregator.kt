package org.nicho.arca.domain.calculation

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.nicho.arca.domain.model.AccountFlow
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.CategorySpend
import org.nicho.arca.domain.model.Granularity
import org.nicho.arca.domain.model.Period
import org.nicho.arca.domain.model.PeriodSummary
import org.nicho.arca.domain.model.RankedSpend
import org.nicho.arca.domain.model.RestSpend
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

    /**
     * Each day's net change from income and expenses (transfers excluded), for the day headers of
     * the transaction list: "Yesterday −542,500". Days are calendar days in [zone]; days with only
     * transfers are 0.
     */
    fun netByDay(txs: List<Transaction>, zone: ZoneId): Map<LocalDate, Long> {
        val totals = LinkedHashMap<LocalDate, Long>()
        for (tx in txs) {
            val day = tx.date.atZone(zone).toLocalDate()
            val current = totals[day] ?: 0L
            totals[day] = when (tx.type) {
                TransactionType.INCOME -> Math.addExact(current, tx.amountMinor)
                TransactionType.EXPENSE -> Math.subtractExact(current, tx.amountMinor)
                else -> current
            }
        }
        return totals
    }

    /**
     * Money into and out of one account in [period], transfers and balance adjustments included:
     * from that account's point of view they really did move money (Account Detail's In / Out /
     * Net).
     */
    fun accountFlow(txs: List<Transaction>, accountId: AccountId, period: Period): AccountFlow {
        var moneyIn = 0L
        var moneyOut = 0L
        for (tx in txs) {
            if (tx.accountId != accountId || !tx.date.isIn(period)) continue
            when (tx.type) {
                TransactionType.INCOME, TransactionType.TRANSFER_IN, TransactionType.ADJUSTMENT_IN ->
                    moneyIn = Math.addExact(moneyIn, tx.amountMinor)
                TransactionType.EXPENSE, TransactionType.TRANSFER_OUT, TransactionType.ADJUSTMENT_OUT ->
                    moneyOut = Math.addExact(moneyOut, tx.amountMinor)
            }
        }
        return AccountFlow(moneyIn, moneyOut, Math.subtractExact(moneyIn, moneyOut))
    }

    /**
     * Whole-percent change from [previous] to [current], e.g. −13 for "down 13%"; null when
     * there's nothing to compare against. Truncated toward zero.
     */
    fun changePercent(previous: Long, current: Long): Int? {
        if (previous == 0L) return null
        val change = Math.multiplyExact(Math.subtractExact(current, previous), 100L) / previous
        return Math.toIntExact(change)
    }

    /**
     * The largest [keep] categories, and everything after them folded into one remainder (the
     * Overview's and Reports' "Other": four bars and a fifth for the rest). [spend] must be
     * largest first, as [spendByCategory] returns it. The remainder is null when nothing is left
     * over.
     */
    fun topWithRest(spend: List<CategorySpend>, keep: Int = 4): RankedSpend {
        val top = spend.take(keep)
        val rest = spend.drop(keep)
        if (rest.isEmpty()) return RankedSpend(top, null)
        var restTotal = 0L
        var share = 0f
        for (item in rest) {
            restTotal = Math.addExact(restTotal, item.totalMinor)
            share += item.share // geometry only (DD-12)
        }
        return RankedSpend(top, RestSpend(restTotal, share, rest.map { it.categoryId }))
    }

    /**
     * Expenditure in each of [periods] (half-open, in order): the Reports trend's weekly bars,
     * where a "week" is whatever span the screen chose (7 days, or a month's last 7–10).
     */
    fun spendInPeriods(txs: List<Transaction>, periods: List<Period>): List<Long> =
        periods.map { period ->
            var total = 0L
            for (tx in txs) {
                if (tx.type == TransactionType.EXPENSE && tx.date.isIn(period)) total = Math.addExact(total, tx.amountMinor)
            }
            total
        }

    /**
     * Income, spending and their difference in each of [periods], in one pass: Reports' trend
     * bars for any of its three measures. [periods] must ascend and not overlap; a transaction in
     * none of them is skipped. Transfers and adjustments never count (DD-10).
     */
    fun summariesInPeriods(txs: List<Transaction>, periods: List<Period>): List<PeriodSummary> {
        val income = LongArray(periods.size)
        val expense = LongArray(periods.size)
        for (tx in txs) {
            if (tx.type !in TransactionType.reportable) continue
            val i = indexOfPeriod(periods, tx.date)
            if (i < 0) continue
            if (tx.type == TransactionType.INCOME) income[i] = Math.addExact(income[i], tx.amountMinor)
            else expense[i] = Math.addExact(expense[i], tx.amountMinor)
        }
        return periods.indices.map { PeriodSummary(income[it], expense[it], Math.subtractExact(income[it], expense[it])) }
    }

    /**
     * [accountFlow] for each of [periods] (ascending, not overlapping), in one pass: what each
     * bar of Account Detail's balance trend brought in and took out.
     */
    fun accountFlowsInPeriods(txs: List<Transaction>, accountId: AccountId, periods: List<Period>): List<AccountFlow> {
        val moneyIn = LongArray(periods.size)
        val moneyOut = LongArray(periods.size)
        for (tx in txs) {
            if (tx.accountId != accountId) continue
            val i = indexOfPeriod(periods, tx.date)
            if (i < 0) continue
            if (tx.type.balanceSign > 0) moneyIn[i] = Math.addExact(moneyIn[i], tx.amountMinor)
            else moneyOut[i] = Math.addExact(moneyOut[i], tx.amountMinor)
        }
        return periods.indices.map { AccountFlow(moneyIn[it], moneyOut[it], Math.subtractExact(moneyIn[it], moneyOut[it])) }
    }

    /**
     * Each value's size against the largest in either direction, keeping its sign: −1..1, for
     * bars that can hang below a zero line (a net, a balance). Geometry only (DD-12).
     */
    fun signedShares(values: List<Long>): List<Float> {
        var largest = 0L
        for (value in values) largest = maxOf(largest, Math.absExact(value))
        return values.map { if (largest == 0L) 0f else it.toFloat() / largest.toFloat() }
    }

    /** The difference [current] − [previous]: how far a net moved between two periods. */
    fun difference(previous: Long, current: Long): Long = Math.subtractExact(current, previous)

    /** What [spend] (one period's categories) adds up to over [ids]: the previous "Other" bar. */
    fun totalFor(spend: List<CategorySpend>, ids: Collection<CategoryId>): Long {
        var total = 0L
        for (item in spend) if (item.categoryId in ids) total = Math.addExact(total, item.totalMinor)
        return total
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

    /** The period holding [instant], by binary search over ascending [periods]; −1 if none does. */
    private fun indexOfPeriod(periods: List<Period>, instant: Instant): Int {
        var low = 0
        var high = periods.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val period = periods[mid]
            when {
                instant < period.start -> high = mid - 1
                instant >= period.endExclusive -> low = mid + 1
                else -> return mid
            }
        }
        return -1
    }

    private fun Transaction.isReportableIn(period: Period): Boolean =
        type in TransactionType.reportable && date.isIn(period)

    private fun Instant.isIn(period: Period): Boolean = this >= period.start && this < period.endExclusive

    private fun bucketOf(instant: Instant, granularity: Granularity, zone: ZoneId): LocalDate {
        val day = instant.atZone(zone).toLocalDate()
        return if (granularity == Granularity.DAY) day else day.withDayOfMonth(1)
    }
}
