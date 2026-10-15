package org.nicho.arca.domain.readmodel

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import org.nicho.arca.domain.calculation.BalanceCalculator
import org.nicho.arca.domain.calculation.EnvelopeCalculator
import org.nicho.arca.domain.calculation.ReportAggregator
import org.nicho.arca.domain.model.BudgetStatus
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.Envelope
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionListItem
import org.nicho.arca.domain.repository.AccountReading
import org.nicho.arca.domain.repository.BudgetStore
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.repository.TransactionReading
import org.nicho.arca.domain.time.PeriodClock

/**
 * SDD-ARC-002 §7.8. The Overview tab's numbers.
 *
 * A read-model assembler: it decides which calculator runs over which flows, so the ViewModel
 * only maps the result to UI state. Keeping it in :domain keeps it JVM-testable and keeps
 * arithmetic *calls* out of :app, where a later edit could slip a sumOf in beside them.
 *
 * Any committed write re-emits through Room's flows (DD-A07). The fold runs on [dispatcher],
 * never the main thread (DD-30). flatMapLatest cancels a superseded month when the date rolls
 * over (DD-A18).
 */
class ObserveSummary(
    private val transactions: TransactionReading,
    private val accounts: AccountReading,
    private val categories: CategoryStore,
    budgets: BudgetStore,
    private val clock: PeriodClock,
    private val recentLimit: Int = 5,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val pipeline = BudgetPipeline(transactions, categories, budgets, clock)

    private class Core(
        val netPositionMinor: Long,
        val inPeriod: List<Transaction>,
        val categoriesById: Map<CategoryId, Category>,
        val recent: List<TransactionListItem>,
        val isEmpty: Boolean,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<SummaryModel> =
        clock.currentMonth.flatMapLatest { current ->
            val previous = clock.periodOf(current.month.minusMonths(1))
            val core = combine(
                transactions.observeBalanceRows(),
                accounts.observeAll(includeArchived = true),
                transactions.observeInPeriod(current.period),
                categories.observeAll(includeArchived = true),
                transactions.observeRecent(recentLimit * 2),
            ) { rows, allAccounts, inPeriod, allCategories, recent ->
                val balances = BalanceCalculator.balances(rows, allAccounts)
                Core(
                    netPositionMinor = BalanceCalculator.netPosition(allAccounts, balances),
                    inPeriod = inPeriod,
                    categoriesById = allCategories.associateBy { it.id },
                    recent = collapseRecent(recent),
                    isEmpty = rows.isEmpty(),
                )
            }
            combine(
                core,
                transactions.observeInPeriod(previous),
                pipeline.observe(flowOf(current.month)),
            ) { c, previousTxs, budget ->
                val summary = ReportAggregator.summary(c.inPeriod, current.period)
                val previousExpense = ReportAggregator.summary(previousTxs, previous).expenseMinor
                val spend = ReportAggregator.spendByCategory(c.inPeriod, c.categoriesById, current.period)
                SummaryModel(
                    month = current.month,
                    netPositionMinor = c.netPositionMinor,
                    summary = summary,
                    budget = budget.summary,
                    leftToSpendFraction = EnvelopeCalculator.leftFraction(
                        budget.summary.totalAvailableMinor,
                        budget.summary.totalLeftMinor,
                    ),
                    needsAttention = needsAttention(budget.summary.envelopes),
                    previousMonthExpenseMinor = previousExpense,
                    expenseChangePercent = ReportAggregator.changePercent(previousExpense, summary.expenseMinor),
                    categorySpend = spend,
                    categoryBars = ReportAggregator.topWithRest(spend, keep = 4),
                    recent = c.recent,
                    categories = c.categoriesById,
                    isEmpty = c.isEmpty,
                )
            }
        }.flowOn(dispatcher)

    // Both legs of a transfer come back from observeRecent; show the transfer once.
    private fun collapseRecent(items: List<TransactionListItem>): List<TransactionListItem> {
        val kept = collapseTransferLegs(items.map { it.transaction }).map { it.id }.toSet()
        return items.filter { it.transaction.id in kept }.take(recentLimit)
    }

    /** Over budget first, then near the limit; within a status, the least left first. */
    private fun needsAttention(envelopes: List<Envelope>): List<Envelope> {
        val rank = mapOf(BudgetStatus.OVER_BUDGET to 0, BudgetStatus.NEAR_LIMIT to 1, BudgetStatus.UNDER_BUDGET to 2)
        return envelopes
            .sortedWith(compareBy<Envelope> { rank[it.status] ?: 3 }.thenBy { EnvelopeCalculator.leftFraction(it.availableMinor, it.leftMinor) })
            .take(3)
    }
}
