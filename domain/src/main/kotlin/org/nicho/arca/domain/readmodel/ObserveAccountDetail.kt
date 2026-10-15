package org.nicho.arca.domain.readmodel

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import org.nicho.arca.domain.calculation.BalanceCalculator
import org.nicho.arca.domain.calculation.ReportAggregator
import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.AccountFlow
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.BalanceRow
import org.nicho.arca.domain.model.Period
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionListItem
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.repository.AccountReading
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.repository.TransactionReading
import org.nicho.arca.domain.time.PeriodClock

/** One month of an account's history, by day. */
data class HistoryMonth(val month: YearMonth, val days: List<HistoryDay>) {
    val items: List<TransactionListItem> get() = days.flatMap { it.items }
}

data class HistoryDay(val date: LocalDate, val items: List<TransactionListItem>)

/** The balance trend's grain: a bar per day, per calendar week (Monday first) or per month. */
enum class BalanceScale { DAILY, WEEKLY, MONTHLY }

/**
 * One bar of the balance trend, [first] to [last] (clipped to the period). [balanceMinor] is the
 * balance at the end of [last]; [fraction] its height against the largest balance in view, keeping
 * the sign so a balance below zero hangs under the zero line (geometry only). [changePercent]
 * compares with the bar before (for the first bar, the balance the period opened with); null from
 * zero. [flow] is what the bar's days brought in and took out.
 */
data class BalancePoint(
    val first: LocalDate,
    val last: LocalDate,
    val balanceMinor: Long,
    val fraction: Float,
    val changePercent: Int?,
    val flow: AccountFlow,
)

/**
 * What Account Detail is looking at (client request, 2026-10-15). [period] drives In / Out / Net,
 * the trend and the history; [scale] is the grain asked for (null: whichever suits the period);
 * [type] narrows the history only; [focus], the first day of a selected bar, narrows In / Out /
 * Net and the history to that bar.
 */
data class AccountDetailSelection(
    val period: PeriodChoice,
    val scale: BalanceScale? = null,
    val type: TypeFilter = TypeFilter.ALL,
    val focus: LocalDate? = null,
)

data class AccountDetailModel(
    val account: Account,
    val balanceMinor: Long,
    val isNegative: Boolean,            // FR-222: shown distinctly, never as an error
    val transactionCount: Int,
    val firstDate: LocalDate?,          // the first entry on this account, if any
    val selection: AccountDetailSelection,
    val firstDay: LocalDate,            // the period as covered: never past today
    val lastDay: LocalDate,
    val scale: BalanceScale,            // the grain drawn
    val scales: List<BalanceScale>,     // the grains this period allows
    val periodFlow: AccountFlow,        // In / Out / Net across the whole period
    val trend: List<BalancePoint>,      // oldest first
    val focus: BalancePoint?,           // the selected bar, when one is
    val history: List<HistoryMonth>,    // newest first: the period's (or the bar's) rows after the type filter
    val shownCount: Int,
) {
    /** In / Out / Net as shown: the selected bar's, or the whole period's. */
    val shownFlow: AccountFlow get() = focus?.flow ?: periodFlow
}

/**
 * Account Detail. The account's own rows are read and prepared once per change to the data; a new
 * period, grain, type or bar only re-slices them, so switching is cheap. Emits null if the
 * account no longer exists.
 */
class ObserveAccountDetail(
    private val transactions: TransactionReading,
    private val accounts: AccountReading,
    private val categories: CategoryStore,
    private val clock: PeriodClock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /** The account's rows, oldest first, with their list rows, prepared once per data change. */
    private class Prepared(
        val account: Account,
        val own: List<Transaction>,
        val items: List<TransactionListItem>, // newest first
        val balanceMinor: Long,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(accountId: AccountId, selections: Flow<AccountDetailSelection>): Flow<AccountDetailModel?> =
        clock.currentMonth.flatMapLatest {
            val prepared = combine(
                transactions.observeForAccount(accountId),
                accounts.observeAll(includeArchived = true),
                categories.observeAll(includeArchived = true),
            ) { own, allAccounts, allCategories ->
                val account = allAccounts.firstOrNull { it.id == accountId } ?: return@combine null
                val sorted = own.sortedBy { it.date }
                // The account's own rows are every row that moves its balance (FR-220).
                val balance = BalanceCalculator.balances(sorted.map { BalanceRow(it.accountId, it.amountMinor, it.type) }, listOf(account))
                    .getValue(accountId)
                Prepared(
                    account = account,
                    own = sorted,
                    items = listItems(own.sortedByDescending { it.date }, allAccounts.associateBy { it.id }, allCategories.associateBy { it.id }),
                    balanceMinor = balance,
                )
            }
            combine(prepared, selections) { p, selection -> p?.let { model(it, selection) } }
        }.flowOn(dispatcher)

    private fun model(p: Prepared, selection: AccountDetailSelection): AccountDetailModel {
        val zone = clock.zone()
        val today = clock.now().atZone(zone).toLocalDate()
        val firstDate = p.own.firstOrNull()?.date?.atZone(zone)?.toLocalDate()
        val (firstDay, lastDay) = days(selection.period, today, firstDate)
        val length = lastDay.toEpochDay() - firstDay.toEpochDay() + 1
        val scales = BalanceScale.entries.filter { allowed(it, length) }
        val scale = selection.scale?.takeIf { it in scales } ?: defaultScale(length)

        val spans = spans(firstDay, lastDay, scale)
        // The balance the period opened with, then the balance at the end of each bar.
        val ends = listOf(firstDay.atStartOfDay(zone).toInstant()) + spans.map { (_, last) -> last.plusDays(1).atStartOfDay(zone).toInstant() }
        val balances = BalanceCalculator.balancesAt(p.account.openingBalanceMinor, p.own, ends)
        val values = balances.drop(1)
        val shares = ReportAggregator.signedShares(values)
        val flows = ReportAggregator.accountFlowsInPeriods(p.own, p.account.id, spans.map { (a, b) -> periodOf(a, b) })
        val trend = spans.mapIndexed { i, (first, last) ->
            BalancePoint(
                first = first,
                last = last,
                balanceMinor = values[i],
                fraction = shares[i],
                changePercent = BalanceCalculator.changePercent(balances[i], values[i]),
                flow = flows[i],
            )
        }
        val focus = selection.focus?.let { day -> trend.firstOrNull { it.first == day } }

        val shownFrom = (focus?.first ?: firstDay).atStartOfDay(zone).toInstant()
        val shownTo = (focus?.last ?: lastDay).plusDays(1).atStartOfDay(zone).toInstant()
        val shown = p.items.filter { item ->
            val tx = item.transaction
            tx.date >= shownFrom && tx.date < shownTo && when (selection.type) {
                TypeFilter.ALL -> true
                TypeFilter.INCOME -> tx.type == TransactionType.INCOME
                TypeFilter.EXPENSE -> tx.type == TransactionType.EXPENSE
                TypeFilter.TRANSFER -> tx.type.isTransferPrincipal
            }
        }
        return AccountDetailModel(
            account = p.account,
            balanceMinor = p.balanceMinor,
            isNegative = BalanceCalculator.isNegative(p.balanceMinor),
            transactionCount = p.own.size,
            firstDate = firstDate,
            selection = selection.copy(scale = scale, focus = focus?.first),
            firstDay = firstDay,
            lastDay = lastDay,
            scale = scale,
            scales = scales,
            periodFlow = ReportAggregator.accountFlow(p.own, p.account.id, periodOf(firstDay, lastDay)),
            trend = trend,
            focus = focus,
            history = shown
                .groupBy { YearMonth.from(it.transaction.date.atZone(zone)) }
                .map { (month, monthItems) ->
                    HistoryMonth(month, monthItems.groupBy { it.transaction.date.atZone(zone).toLocalDate() }.map { (day, dayItems) -> HistoryDay(day, dayItems) })
                },
            shownCount = shown.size,
        )
    }

    /** The days [choice] covers, never past today. "All time" starts at the first entry. */
    private fun days(choice: PeriodChoice, today: LocalDate, firstDate: LocalDate?): Pair<LocalDate, LocalDate> {
        val (first, last) = when (choice) {
            is PeriodChoice.Month -> choice.month.atDay(1) to choice.month.atEndOfMonth()
            is PeriodChoice.LastDays -> today.minusDays(choice.days - 1L) to today
            is PeriodChoice.Range -> choice.from to choice.to
            PeriodChoice.AllTime -> (firstDate ?: today) to today
        }
        val end = minOf(last, today)
        return minOf(first, end) to end
    }

    private fun periodOf(first: LocalDate, last: LocalDate): Period {
        val zone = clock.zone()
        return Period(first.atStartOfDay(zone).toInstant(), last.plusDays(1).atStartOfDay(zone).toInstant())
    }

    companion object {
        /** A grain is offered when it gives a readable number of bars: 2 or more, about 100 at most. */
        fun allowed(scale: BalanceScale, days: Long): Boolean = when (scale) {
            BalanceScale.DAILY -> days <= 93
            BalanceScale.WEEKLY -> days in 8..731
            BalanceScale.MONTHLY -> days >= 45
        }

        /** What a period opens on: days for a month or less, weeks up to about four months, then months. */
        fun defaultScale(days: Long): BalanceScale = when {
            days <= 31 -> BalanceScale.DAILY
            days <= 120 -> BalanceScale.WEEKLY
            else -> BalanceScale.MONTHLY
        }

        /** The bars: each day, each Monday-first week, or each month, clipped to [first]..[last]. */
        fun spans(first: LocalDate, last: LocalDate, scale: BalanceScale): List<Pair<LocalDate, LocalDate>> {
            val starts = when (scale) {
                BalanceScale.DAILY -> generateSequence(first) { it.plusDays(1) }
                BalanceScale.WEEKLY -> generateSequence(first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) { it.plusWeeks(1) }
                BalanceScale.MONTHLY -> generateSequence(first.withDayOfMonth(1)) { it.plusMonths(1) }
            }.takeWhile { !it.isAfter(last) }.toList()
            return starts.map { start ->
                val end = when (scale) {
                    BalanceScale.DAILY -> start
                    BalanceScale.WEEKLY -> start.plusDays(6)
                    BalanceScale.MONTHLY -> start.plusMonths(1).minusDays(1)
                }
                maxOf(start, first) to minOf(end, last)
            }
        }
    }
}
