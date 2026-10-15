package org.nicho.arca.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.readmodel.AccountDetailSelection
import org.nicho.arca.domain.readmodel.BalanceScale
import org.nicho.arca.domain.readmodel.ObserveAccountDetail
import org.nicho.arca.domain.readmodel.PeriodChoice
import org.nicho.arca.domain.time.PeriodClock
import org.nicho.arca.domain.readmodel.TypeFilter
import org.nicho.arca.domain.repository.AccountWriting
import org.nicho.arca.navigation.AccountDetailRoute

/**
 * Account Detail: the balance, a chosen period's In / Out / Net, its balance trend and history
 * (FR-107), favourite and archive (FR-202, FR-205). Opens on this month. Picking a bar narrows
 * the figures and the history to it; a new period or grain clears that.
 */
@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeAccountDetail: ObserveAccountDetail,
    clock: PeriodClock,
    private val writing: AccountWriting,
) : ViewModel() {

    val accountId = AccountId(UUID.fromString(savedStateHandle.toRoute<AccountDetailRoute>().accountId))
    private val selection = MutableStateFlow(AccountDetailSelection(PeriodChoice.Month(clock.currentYearMonth())))

    val state: StateFlow<AccountDetailUiState> =
        observeAccountDetail(accountId, selection)
            .map { model -> if (model == null) AccountDetailUiState.Gone else AccountDetailUiState.Ready(model) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountDetailUiState.Loading)

    fun onPeriodChange(period: PeriodChoice) = selection.update { it.copy(period = period, focus = null) }

    fun onScaleChange(scale: BalanceScale) = selection.update { it.copy(scale = scale, focus = null) }

    /** A bar picked on the trend (its first day), or null to see the whole period again. */
    fun onFocus(first: LocalDate?) = selection.update { it.copy(focus = first) }

    fun onFilterChange(type: TypeFilter) = selection.update { it.copy(type = type) }

    fun onToggleFavorite() = update { it.copy(isFavorite = !it.isFavorite) }

    fun onToggleArchived() = update { it.copy(isArchived = !it.isArchived) }

    private fun update(change: (org.nicho.arca.domain.model.Account) -> org.nicho.arca.domain.model.Account) {
        val ready = state.value as? AccountDetailUiState.Ready ?: return
        viewModelScope.launch { writing.update(change(ready.model.account)) }
    }
}
