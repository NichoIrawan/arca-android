package org.nicho.arca.feature.transactions

import org.nicho.arca.domain.model.AccountId

import org.nicho.arca.domain.model.TransactionListItem

sealed interface TransactionDetailUiState {
    data object Loading : TransactionDetailUiState
    data object Gone : TransactionDetailUiState

    /**
     * [primary] is the row the user opened, or the outgoing leg when they opened any member of a
     * transfer. [members] holds every row of the transfer (empty for income/expense).
     */
    data class Ready(
        val primary: TransactionListItem,
        val members: List<TransactionListItem>,
        val accountIcons: Map<AccountId, String> = emptyMap(), // for the From / To tiles
    ) : TransactionDetailUiState {
        val isTransfer: Boolean get() = primary.transaction.transferGroupId != null
    }
}
