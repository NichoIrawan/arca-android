package org.nicho.arca.domain.readmodel

import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import org.nicho.arca.domain.calculation.ReportAggregator
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.model.CategorySpend
import org.nicho.arca.domain.model.EnvelopeSummary
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.repository.BudgetStore
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.repository.TransactionReading
import org.nicho.arca.domain.time.PeriodClock

/** Where the shown month stands: closed ("Left over"), in progress ("Left to spend"), or ahead ("Available"). */
enum class MonthState { CLOSED, CURRENT, AHEAD }

/** One income category in the month, received or not: "Salary · 1 transaction · +8,000,000". */
data class IncomeRow(val category: Category, val totalMinor: Long, val count: Int)

/** What the Budget (Anggaran) tab shows for one month. */
data class BudgetModel(
    val summary: EnvelopeSummary,
    val categories: Map<CategoryId, Category>,   // display fields for each envelope's categoryId
    val income: List<CategorySpend>,             // what was received; income has no envelopes
    val incomeRows: List<IncomeRow> = emptyList(), // every active income category, received or not
    val receivedMinor: Long = 0,                 // the month's income total
    val archivedCounts: Map<CategoryId, Int> = emptyMap(), // "3 transactions" under each archived category
    val monthState: MonthState = MonthState.CURRENT,
    val today: LocalDate = LocalDate.MIN,
)

/**
 * SDD-ARC-002 §9.12. The Budget tab for whichever month the user has navigated to. Every money
 * figure on that screen, the "Over by" amount included, comes from here: the ViewModel formats,
 * it doesn't add, subtract or compare (DD-21).
 */
class ObserveBudget(
    private val transactions: TransactionReading,
    categories: CategoryStore,
    budgets: BudgetStore,
    private val clock: PeriodClock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val pipeline = BudgetPipeline(transactions, categories, budgets, clock)

    operator fun invoke(months: Flow<YearMonth>): Flow<BudgetModel> =
        combine(pipeline.observe(months), transactions.observeCountsByCategory(), clock.currentMonth) { snapshot, counts, current ->
            val inMonth = snapshot.transactions.filter { it.date >= snapshot.period.start && it.date < snapshot.period.endExclusive }
            val income = ReportAggregator.incomeByCategory(inMonth, snapshot.categories, snapshot.period)
            val incomeById = income.associateBy { it.categoryId }
            val incomeCounts = inMonth.filter { it.type == TransactionType.INCOME }.mapNotNull { it.categoryId }.groupingBy { it }.eachCount()
            BudgetModel(
                summary = snapshot.summary,
                categories = snapshot.categories,
                income = income,
                incomeRows = snapshot.categories.values
                    .filter { it.kind == CategoryKind.INCOME && (!it.isArchived || it.id in incomeById) }
                    .map { IncomeRow(it, incomeById[it.id]?.totalMinor ?: 0L, incomeCounts[it.id] ?: 0) }
                    .sortedWith(compareByDescending<IncomeRow> { it.totalMinor }.thenBy { it.category.name }),
                receivedMinor = ReportAggregator.summary(inMonth, snapshot.period).incomeMinor,
                archivedCounts = counts,
                monthState = when {
                    snapshot.month < current.month -> MonthState.CLOSED
                    snapshot.month > current.month -> MonthState.AHEAD
                    else -> MonthState.CURRENT
                },
                today = clock.now().atZone(clock.zone()).toLocalDate(),
            )
        }.flowOn(dispatcher)
}
