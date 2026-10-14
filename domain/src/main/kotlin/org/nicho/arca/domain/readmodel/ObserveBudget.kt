package org.nicho.arca.domain.readmodel

import java.time.YearMonth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.nicho.arca.domain.calculation.ReportAggregator
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.CategorySpend
import org.nicho.arca.domain.model.EnvelopeSummary
import org.nicho.arca.domain.repository.BudgetStore
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.repository.TransactionReading
import org.nicho.arca.domain.time.PeriodClock

/** What the Budget (Anggaran) tab shows for one month. */
data class BudgetModel(
    val summary: EnvelopeSummary,
    val categories: Map<CategoryId, Category>,   // display fields for each envelope's categoryId
    val income: List<CategorySpend>,             // what was received; income has no envelopes
)

/**
 * SDD-ARC-002 §9.12. The Budget tab for whichever month the user has navigated to. Every money
 * figure on that screen, the "Over by" amount included, comes from here: the ViewModel formats,
 * it doesn't add, subtract or compare (DD-21).
 */
class ObserveBudget(
    transactions: TransactionReading,
    categories: CategoryStore,
    budgets: BudgetStore,
    clock: PeriodClock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val pipeline = BudgetPipeline(transactions, categories, budgets, clock)

    operator fun invoke(months: Flow<YearMonth>): Flow<BudgetModel> =
        pipeline.observe(months).map { snapshot ->
            BudgetModel(
                summary = snapshot.summary,
                categories = snapshot.categories,
                income = ReportAggregator.incomeByCategory(snapshot.transactions, snapshot.categories, snapshot.period),
            )
        }.flowOn(dispatcher)
}
