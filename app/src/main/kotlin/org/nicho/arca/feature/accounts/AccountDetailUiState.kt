package org.nicho.arca.feature.accounts

import org.nicho.arca.domain.readmodel.AccountDetailModel
import org.nicho.arca.domain.readmodel.TypeFilter

sealed interface AccountDetailUiState {
    data object Loading : AccountDetailUiState
    data object Gone : AccountDetailUiState
    data class Ready(val model: AccountDetailModel) : AccountDetailUiState {
        val filter: TypeFilter get() = model.selection.type
    }
}
