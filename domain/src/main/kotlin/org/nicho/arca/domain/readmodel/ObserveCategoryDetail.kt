package org.nicho.arca.domain.readmodel

import java.time.YearMonth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.CategorySpend
import org.nicho.arca.domain.model.Envelope
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.repository.BudgetStore
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.repository.TransactionReading
import org.nicho.arca.domain.time.PeriodClock

/** One category in one month. Exactly one of [envelope] and [notBudgeted] is set for an expense category. */
data class CategoryDetailModel(
    val category: Category,
    val month: YearMonth,
    val envelope: Envelope?,            // budget · spent · carried
    val notBudgeted: CategorySpend?,    // the "Not budgeted" state with its spend (FR-266)
    val transactions: List<Transaction>, // every one, newest first, after the account filter (FR-268)
    val accountIds: List<AccountId>,    // accounts used this month: the filter chips
)

/**
 * SDD-ARC-002 §9.12 · FR-264, FR-268. The account filter narrows the list only; the envelope's
 * figures are always the whole category's, because a budget belongs to the category, not to an
 * account. Emits null if the category no longer exists.
 */
class ObserveCategoryDetail(
    transactions: TransactionReading,
    categories: CategoryStore,
    budgets: BudgetStore,
    clock: PeriodClock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val pipeline = BudgetPipeline(transactions, categories, budgets, clock)

    operator fun invoke(
        categoryId: CategoryId,
        months: Flow<YearMonth>,
        accountFilter: Flow<AccountId?>,
    ): Flow<CategoryDetailModel?> =
        combine(pipeline.observe(months), accountFilter) { snapshot, account ->
            val category = snapshot.categories[categoryId] ?: return@combine null
            val inCategory = snapshot.transactions
                .filter {
                    it.categoryId == categoryId &&
                        it.date >= snapshot.period.start && it.date < snapshot.period.endExclusive
                }
                .sortedByDescending { it.date }
            CategoryDetailModel(
                category = category,
                month = snapshot.month,
                envelope = snapshot.summary.envelopes.firstOrNull { it.categoryId == categoryId },
                notBudgeted = snapshot.summary.notBudgeted.firstOrNull { it.categoryId == categoryId },
                transactions = if (account == null) inCategory else inCategory.filter { it.accountId == account },
                accountIds = inCategory.map { it.accountId }.distinct(),
            )
        }.flowOn(dispatcher)
}
