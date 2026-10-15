package org.nicho.arca.feature.transactions

import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.readmodel.TransactionFilter
import org.nicho.arca.domain.readmodel.TransactionListModel

sealed interface TransactionListUiState {
    data object Loading : TransactionListUiState

    data class Ready(
        val model: TransactionListModel,
        val accounts: List<Account>,       // for the filter sheet
        val categories: List<Category>,
    ) : TransactionListUiState {
        val filter: TransactionFilter get() = model.filter
    }
}
