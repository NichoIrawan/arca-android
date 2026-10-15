package org.nicho.arca.feature.accounts

import org.nicho.arca.domain.readmodel.AccountsModel

sealed interface AccountListUiState {
    data object Loading : AccountListUiState
    data object Empty : AccountListUiState
    data class Ready(val model: AccountsModel) : AccountListUiState
}
