package org.nicho.arca.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.readmodel.ObserveTransactionList
import org.nicho.arca.domain.readmodel.PeriodChoice
import org.nicho.arca.domain.readmodel.TransactionFilter
import org.nicho.arca.domain.repository.AccountReading
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.undo.UndoCoordinator

/** The Transactions tab (FR-107–109): filtered, grouped by day, swipe to delete with undo. */
@HiltViewModel
class TransactionListViewModel @Inject constructor(
    private val observeTransactionList: ObserveTransactionList,
    accounts: AccountReading,
    categories: CategoryStore,
    private val undo: UndoCoordinator,
) : ViewModel() {

    private val filter = MutableStateFlow(defaultFilter())
    private val draft = MutableStateFlow<TransactionFilter?>(null)

    val state: StateFlow<TransactionListUiState> = combine(
        observeTransactionList(filter),
        undo.pending,
        accounts.observeAll(includeArchived = true),
        categories.observeAll(includeArchived = true),
    ) { model, pending, allAccounts, allCategories ->
        val days = if (pending == null) {
            model.days
        } else {
            model.days
                .map { day -> day.copy(items = day.items.filterNot { pending.hides(it.transaction) }) }
                .filter { it.items.isNotEmpty() }
        }
        TransactionListUiState.Ready(model.copy(days = days), allAccounts, allCategories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionListUiState.Loading)

    /** How many rows the Filter sheet's unapplied choice would show: "Apply · 6 results". */
    @OptIn(ExperimentalCoroutinesApi::class)
    val draftCount: StateFlow<Int?> = draft
        .flatMapLatest { d -> if (d == null) flowOf(null) else observeTransactionList(flowOf(d)).map { it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onDraftChange(newDraft: TransactionFilter) {
        draft.value = newDraft
    }

    fun onApplyFilter(newFilter: TransactionFilter) {
        filter.value = newFilter
        draft.value = null
    }

    fun onResetFilter() {
        filter.value = defaultFilter()
    }

    /** FR-106. Any member of a transfer (a fee included) deletes the whole group (UXD §4.4). */
    fun onDelete(tx: Transaction) {
        val group = tx.transferGroupId
        if (group != null) undo.deleteTransfer(group) else undo.deleteTransaction(tx.id)
    }

    private fun defaultFilter() = TransactionFilter(PeriodChoice.Month(YearMonth.now()))
}
