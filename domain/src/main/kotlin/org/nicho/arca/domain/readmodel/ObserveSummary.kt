package org.nicho.arca.domain.readmodel

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import org.nicho.arca.domain.calculation.BalanceCalculator
import org.nicho.arca.domain.calculation.ReportAggregator
import org.nicho.arca.domain.repository.AccountReading
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.repository.TransactionReading
import org.nicho.arca.domain.time.PeriodClock

/**
 * SDD-ARC-002 §7.8. The Summary tab's numbers.
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
    private val clock: PeriodClock,
    private val recentLimit: Int = 5,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<SummaryModel> =
        clock.currentMonth.flatMapLatest { current ->
            combine(
                transactions.observeBalanceRows(),
                accounts.observeAll(includeArchived = true),
                transactions.observeInPeriod(current.period),
                categories.observeAll(includeArchived = true),
                transactions.observeRecent(recentLimit),
            ) { rows, allAccounts, inPeriod, allCategories, recent ->
                val balances = BalanceCalculator.balances(rows, allAccounts)
                SummaryModel(
                    month = current.month,
                    netPositionMinor = BalanceCalculator.netPosition(allAccounts, balances),
                    summary = ReportAggregator.summary(inPeriod, current.period),
                    categorySpend = ReportAggregator.spendByCategory(
                        inPeriod,
                        allCategories.associateBy { it.id },
                        current.period,
                    ),
                    recent = recent,
                    isEmpty = rows.isEmpty(),
                )
            }
        }.flowOn(dispatcher)
}
