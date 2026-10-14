package org.nicho.arca.domain.readmodel

import java.time.YearMonth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import org.nicho.arca.domain.calculation.ReportAggregator
import org.nicho.arca.domain.model.Granularity
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.repository.TransactionReading
import org.nicho.arca.domain.time.PeriodClock

/**
 * The Reports screen for whichever month the user has navigated to (FR-304). The ViewModel
 * owns that month as state and passes it in as months; switching months cancels the previous
 * month's queries.
 */
class ObserveReports(
    private val transactions: TransactionReading,
    private val categories: CategoryStore,
    private val clock: PeriodClock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(months: Flow<YearMonth>): Flow<ReportsModel> =
        months.flatMapLatest { month ->
            val period = clock.periodOf(month)
            combine(
                transactions.observeInPeriod(period),
                categories.observeAll(includeArchived = true),
            ) { txs, allCategories ->
                ReportsModel(
                    month = month,
                    summary = ReportAggregator.summary(txs, period),
                    spendByCategory = ReportAggregator.spendByCategory(txs, allCategories.associateBy { it.id }, period),
                    dailySpend = ReportAggregator.spendOverTime(txs, period, Granularity.DAY, clock.zone()),
                )
            }
        }.flowOn(dispatcher)
}
