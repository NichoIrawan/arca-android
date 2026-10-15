package org.nicho.arca.feature.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionId
import org.nicho.arca.domain.model.TransactionListItem
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.repository.AccountReading
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.repository.TransactionReading
import org.nicho.arca.navigation.TransactionDetailRoute
import org.nicho.arca.undo.UndoCoordinator

/** Transaction Detail (FR-105, FR-106). Edits and deletes act on the whole transfer (UXD §4.4). */
@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    reading: TransactionReading,
    accounts: AccountReading,
    categories: CategoryStore,
    private val undo: UndoCoordinator,
) : ViewModel() {

    val transactionId = TransactionId(UUID.fromString(savedStateHandle.toRoute<TransactionDetailRoute>().transactionId))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rows = reading.observeById(transactionId).flatMapLatest { tx ->
        val group = tx?.transferGroupId
        if (tx == null) flowOf(emptyList()) else if (group == null) flowOf(listOf(tx)) else reading.observeGroup(group)
    }

    val state: StateFlow<TransactionDetailUiState> = combine(
        rows,
        accounts.observeAll(includeArchived = true),
        categories.observeAll(includeArchived = true),
        undo.pending,
    ) { members, allAccounts, allCategories, pending ->
        if (members.isEmpty() || members.any { pending?.hides(it) == true }) return@combine TransactionDetailUiState.Gone
        val accountsById = allAccounts.associateBy { it.id }
        val categoriesById = allCategories.associateBy { it.id }
        val items = members.mapNotNull { it.toItem(accountsById, categoriesById) }
        val primary = items.firstOrNull { it.transaction.type == TransactionType.TRANSFER_OUT }
            ?: items.firstOrNull { it.transaction.id == transactionId }
            ?: return@combine TransactionDetailUiState.Gone
        TransactionDetailUiState.Ready(
            primary = primary,
            members = if (primary.transaction.transferGroupId != null) items.sortedBy { ORDER.indexOf(it.transaction.type) } else emptyList(),
            accountIcons = allAccounts.associate { it.id to it.icon },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionDetailUiState.Loading)

    /** Deferred, with undo (DD-A11). The screen closes; the shell shows the undo banner. */
    fun onDelete() {
        val ready = state.value as? TransactionDetailUiState.Ready ?: return
        val group = ready.primary.transaction.transferGroupId
        if (group != null) undo.deleteTransfer(group) else undo.deleteTransaction(ready.primary.transaction.id)
    }

    private fun Transaction.toItem(
        accounts: Map<org.nicho.arca.domain.model.AccountId, Account>,
        categories: Map<org.nicho.arca.domain.model.CategoryId, Category>,
    ): TransactionListItem? {
        val account = accounts[accountId] ?: return null
        val category = categoryId?.let(categories::get)
        return TransactionListItem(
            transaction = this,
            accountName = account.name,
            accountColorHex = account.colorHex,
            counterAccountName = counterAccountId?.let { accounts[it]?.name },
            categoryName = category?.name,
            categoryIcon = category?.icon,
            categoryColorHex = category?.colorHex,
        )
    }

    private companion object {
        val ORDER = listOf(TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN, TransactionType.EXPENSE)
    }
}
