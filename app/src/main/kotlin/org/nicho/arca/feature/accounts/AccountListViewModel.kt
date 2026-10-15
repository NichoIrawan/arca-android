package org.nicho.arca.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.nicho.arca.domain.readmodel.ObserveAccounts

/** The Accounts tab (FR-204–206, FR-220, FR-221). Maps the read model; never sums. */
@HiltViewModel
class AccountListViewModel @Inject constructor(observeAccounts: ObserveAccounts) : ViewModel() {
    val state: StateFlow<AccountListUiState> = observeAccounts()
        .map { model ->
            if (model.groups.isEmpty() && model.archived.isEmpty()) AccountListUiState.Empty else AccountListUiState.Ready(model)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountListUiState.Loading)
}
