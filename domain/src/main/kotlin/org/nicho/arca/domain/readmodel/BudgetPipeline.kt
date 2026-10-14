package org.nicho.arca.domain.readmodel

import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.nicho.arca.domain.calculation.EnvelopeCalculator
import org.nicho.arca.domain.calculation.ReportAggregator
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.CategorySpend
import org.nicho.arca.domain.model.EnvelopeSummary
import org.nicho.arca.domain.model.Period
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.repository.BudgetStore
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.repository.TransactionReading
import org.nicho.arca.domain.time.PeriodClock

/** One evaluation of the budget for a month, plus the inputs it was computed from. */
internal class BudgetSnapshot(
    val month: YearMonth,
    val period: Period,
    val summary: EnvelopeSummary,
    val categories: Map<CategoryId, Category>,
    val transactions: List<Transaction>,   // from the earliest allocation month through `month`
)

/**
 * SDD-ARC-002 §7.9. Shared by ObserveBudget and ObserveCategoryDetail so the envelope fold has
 * one wiring. Carry needs every month since the first allocation, so it reads transactions from
 * that month through the selected one. Room re-runs the query on any write, which is what makes
 * a backdated expense correct every later carry with nothing stored to repair (DD-34).
 *
 * The cost to watch is that input range (§14.3, RSK-A4). If it matters, the remedy is a narrow
 * column projection like DD-A10's, never a SQL SUM.
 */
internal class BudgetPipeline(
    private val transactions: TransactionReading,
    private val categories: CategoryStore,
    private val budgets: BudgetStore,
    private val clock: PeriodClock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(months: Flow<YearMonth>): Flow<BudgetSnapshot> {
        val earliestAllocation = budgets.observeAllocations()
            .map { allocations -> allocations.minOfOrNull { it.month } }
            .distinctUntilChanged()
        return combine(months, earliestAllocation) { month, earliest -> month to earliest }
            .distinctUntilChanged()
            .flatMapLatest { (month, earliest) ->
                val first = if (earliest != null && earliest < month) earliest else month
                val range = Period(clock.periodOf(first).start, clock.periodOf(month).endExclusive)
                combine(
                    transactions.observeInPeriod(range),
                    categories.observeAll(includeArchived = true),
                    budgets.observeAllocations(),
                ) { txs, allCategories, allocations ->
                    val byId = allCategories.associateBy { it.id }
                    BudgetSnapshot(
                        month = month,
                        period = clock.periodOf(month),
                        summary = EnvelopeCalculator.evaluate(
                            month = month,
                            categories = allCategories,
                            allocations = allocations,
                            spendByMonth = spendByMonth(txs, byId, first, month),
                        ),
                        categories = byId,
                        transactions = txs,
                    )
                }
            }
    }

    // Groups transactions by calendar month first, so each month's aggregation reads only its own
    // rows instead of rescanning the whole range.
    private fun spendByMonth(
        txs: List<Transaction>,
        categories: Map<CategoryId, Category>,
        first: YearMonth,
        last: YearMonth,
    ): Map<YearMonth, List<CategorySpend>> {
        val zone = clock.zone()
        val byMonth = txs.groupBy { YearMonth.from(it.date.atZone(zone)) }
        val result = LinkedHashMap<YearMonth, List<CategorySpend>>()
        var m = first
        while (m <= last) {
            result[m] = ReportAggregator.spendByCategory(byMonth[m].orEmpty(), categories, clock.periodOf(m))
            m = m.plusMonths(1)
        }
        return result
    }
}
