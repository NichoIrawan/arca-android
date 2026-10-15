package org.nicho.arca.feature.budget

import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.readmodel.CategoryDetailModel
import org.nicho.arca.domain.readmodel.SortOrder

sealed interface CategoryDetailUiState {
    data object Loading : CategoryDetailUiState
    data object Gone : CategoryDetailUiState

    data class Ready(
        val model: CategoryDetailModel,
        val filterAccounts: List<Account>,   // accounts used this month, for the chips
        val selectedAccount: AccountId?,
        val sort: SortOrder,
    ) : CategoryDetailUiState
}
