package org.nicho.arca.domain.readmodel

import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.nicho.arca.domain.calculation.ReportAggregator
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.Period
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.repository.AccountReading
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.repository.TransactionReading
import org.nicho.arca.domain.time.PeriodClock

/** Which dates a list covers (Filter: "Period"). */
sealed interface PeriodChoice {
    data class Month(val month: YearMonth) : PeriodChoice
    /** The last [days] days, today included. */
    data class LastDays(val days: Int) : PeriodChoice
    /** [from] through [to], both included. */
    data class Range(val from: LocalDate, val to: LocalDate) : PeriodChoice
    /** Everything up to today. */
    data object AllTime : PeriodChoice
}

enum class TypeFilter { ALL, EXPENSE, INCOME, TRANSFER }

/** FR-108: combinable filters. Empty sets mean "all". */
data class TransactionFilter(
    val period: PeriodChoice,
    val type: TypeFilter = TypeFilter.ALL,
    val accountIds: Set<AccountId> = emptySet(),
    val categoryIds: Set<CategoryId> = emptySet(),
    val sort: SortOrder = SortOrder.NEWEST,
    /** Searches notes, case-insensitively (Search Transactions). Blank means no search. */
    val query: String = "",
)

data class TransactionListModel(
    val filter: TransactionFilter,
    val period: Period,
    val expenseMinor: Long,     // header: "Rp5.35M out this month"
    val expenseCount: Int,      // "· 81 expenses"
    val count: Int,
    val days: List<TransactionDay>,
    val ledgerIsEmpty: Boolean, // no transactions at all, as opposed to none matching the filter
)

/**
 * The Transactions tab (FR-107, FR-108): the filtered rows, grouped by day with each day's net.
 * The ViewModel owns the filter and passes it in; changing it cancels the previous query.
 */
class ObserveTransactionList(
    private val transactions: TransactionReading,
    private val accounts: AccountReading,
    private val categories: CategoryStore,
    private val clock: PeriodClock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(filters: Flow<TransactionFilter>): Flow<TransactionListModel> =
        filters.flatMapLatest { filter ->
            val period = resolve(filter.period)
            combine(
                transactions.observeInPeriod(period),
                accounts.observeAll(includeArchived = true),
                categories.observeAll(includeArchived = true),
                transactions.observeHasAny().map { !it },
            ) { inPeriod, allAccounts, allCategories, ledgerIsEmpty ->
                val matching = inPeriod.filter { it.matches(filter) }
                val shown = collapseTransferLegs(matching)
                val zone = clock.zone()
                val items = listItems(shown, allAccounts.associateBy { it.id }, allCategories.associateBy { it.id })
                val summary = ReportAggregator.summary(matching, period)
                TransactionListModel(
                    filter = filter,
                    period = period,
                    expenseMinor = summary.expenseMinor,
                    expenseCount = matching.count { it.type == TransactionType.EXPENSE },
                    count = shown.size,
                    days = groupIntoDays(items, filter.sort, ReportAggregator.netByDay(matching, zone)) {
                        it.transaction.date.atZone(zone).toLocalDate()
                    },
                    ledgerIsEmpty = ledgerIsEmpty,
                )
            }
        }.flowOn(dispatcher)

    /** The instants a [PeriodChoice] covers, in the current zone. */
    fun resolve(choice: PeriodChoice): Period {
        val zone = clock.zone()
        return when (choice) {
            is PeriodChoice.Month -> clock.periodOf(choice.month)
            is PeriodChoice.LastDays -> {
                val today = clock.now().atZone(zone).toLocalDate()
                Period(
                    today.minusDays(choice.days - 1L).atStartOfDay(zone).toInstant(),
                    today.plusDays(1).atStartOfDay(zone).toInstant(),
                )
            }
            is PeriodChoice.Range -> Period(
                choice.from.atStartOfDay(zone).toInstant(),
                choice.to.plusDays(1).atStartOfDay(zone).toInstant(),
            )
            PeriodChoice.AllTime -> Period(
                java.time.Instant.EPOCH,
                clock.now().atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant(),
            )
        }
    }

    private fun Transaction.matches(filter: TransactionFilter): Boolean {
        val typeOk = when (filter.type) {
            TypeFilter.ALL -> true
            TypeFilter.EXPENSE -> type == TransactionType.EXPENSE
            TypeFilter.INCOME -> type == TransactionType.INCOME
            TypeFilter.TRANSFER -> type.isTransferPrincipal
        }
        val accountOk = filter.accountIds.isEmpty() || accountId in filter.accountIds
        val categoryOk = filter.categoryIds.isEmpty() || (categoryId != null && categoryId in filter.categoryIds)
        val queryOk = filter.query.isBlank() || note?.contains(filter.query.trim(), ignoreCase = true) == true
        return typeOk && accountOk && categoryOk && queryOk
    }
}
