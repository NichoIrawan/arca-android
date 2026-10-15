package org.nicho.arca.domain.readmodel

import java.time.YearMonth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import org.nicho.arca.domain.calculation.EnvelopeCalculator
import org.nicho.arca.domain.calculation.ReportAggregator
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.model.CategorySpend
import org.nicho.arca.domain.model.Envelope
import org.nicho.arca.domain.repository.AccountReading
import org.nicho.arca.domain.repository.BudgetStore
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.repository.TransactionReading
import org.nicho.arca.domain.time.PeriodClock

/** One category in one month. For an expense category, exactly one of [envelope] and [notBudgeted] is set. */
data class CategoryDetailModel(
    val category: Category,
    val month: YearMonth,
    val envelope: Envelope?,            // budget · spent · carried in
    val leftFraction: Float,            // the envelope bar, geometry only
    val notBudgeted: CategorySpend?,    // the "Not budgeted" state with its spend (FR-266)
    val count: Int,                     // after the account filter
    val shownTotalMinor: Long = 0,      // what those transactions add up to: "18 transactions · Rp2,250,000"
    val monthState: MonthState = MonthState.CURRENT,
    val days: List<TransactionDay>,     // every transaction, nothing truncated (FR-268)
    val accountIds: List<AccountId>,    // accounts used this month: the filter chips
)

/**
 * SDD-ARC-002 §9.12 · FR-264, FR-268. The account filter narrows the list only; the envelope's
 * figures always cover the whole category, because a budget belongs to the category, not to an
 * account. Emits null if the category no longer exists.
 */
class ObserveCategoryDetail(
    private val transactions: TransactionReading,
    private val accounts: AccountReading,
    categories: CategoryStore,
    budgets: BudgetStore,
    private val clock: PeriodClock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val pipeline = BudgetPipeline(transactions, categories, budgets, clock)

    operator fun invoke(
        categoryId: CategoryId,
        months: Flow<YearMonth>,
        accountFilter: Flow<AccountId?>,
        sort: Flow<SortOrder>,
    ): Flow<CategoryDetailModel?> =
        combine(
            pipeline.observe(months),
            accounts.observeAll(includeArchived = true),
            accountFilter,
            sort,
        ) { snapshot, allAccounts, account, order ->
            val category = snapshot.categories[categoryId] ?: return@combine null
            val inCategory = snapshot.transactions.filter {
                it.categoryId == categoryId &&
                    it.date >= snapshot.period.start && it.date < snapshot.period.endExclusive
            }
            val shown = if (account == null) inCategory else inCategory.filter { it.accountId == account }
            val zone = clock.zone()
            val envelope = snapshot.summary.envelopes.firstOrNull { it.categoryId == categoryId }
            CategoryDetailModel(
                category = category,
                month = snapshot.month,
                envelope = envelope,
                leftFraction = envelope?.let { EnvelopeCalculator.leftFraction(it.availableMinor, it.leftMinor) } ?: 0f,
                notBudgeted = snapshot.summary.notBudgeted.firstOrNull { it.categoryId == categoryId },
                count = shown.size,
                shownTotalMinor = ReportAggregator.summary(shown, snapshot.period).let {
                    if (category.kind == CategoryKind.INCOME) it.incomeMinor else it.expenseMinor
                },
                monthState = when {
                    snapshot.month < clock.currentYearMonth() -> MonthState.CLOSED
                    snapshot.month > clock.currentYearMonth() -> MonthState.AHEAD
                    else -> MonthState.CURRENT
                },
                days = groupIntoDays(
                    listItems(shown, allAccounts.associateBy { it.id }, snapshot.categories),
                    order,
                    ReportAggregator.netByDay(shown, zone),
                ) { it.transaction.date.atZone(zone).toLocalDate() },
                accountIds = inCategory.map { it.accountId }.distinct(),
            )
        }.flowOn(dispatcher)
}
