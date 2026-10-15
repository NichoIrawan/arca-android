package org.nicho.arca.feature.budget

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.readmodel.ObserveCategoryDetail
import org.nicho.arca.domain.readmodel.SortOrder
import org.nicho.arca.domain.repository.AccountReading
import org.nicho.arca.navigation.CategoryDetailRoute
import org.nicho.arca.undo.UndoCoordinator

/** Category Detail (FR-264, FR-268): every transaction, a sticky account filter, the envelope's figures. */
@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeCategoryDetail: ObserveCategoryDetail,
    accounts: AccountReading,
    undo: UndoCoordinator,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CategoryDetailRoute>()
    val categoryId = CategoryId(UUID.fromString(route.categoryId))
    private val month = MutableStateFlow(YearMonth.of(route.year, route.month))
    private val account = MutableStateFlow<AccountId?>(null)
    private val sort = MutableStateFlow(SortOrder.NEWEST)

    val state: StateFlow<CategoryDetailUiState> = combine(
        observeCategoryDetail(categoryId, month, account, sort),
        accounts.observeAll(includeArchived = true),
        undo.pending,
    ) { model, allAccounts, pending ->
        if (model == null) return@combine CategoryDetailUiState.Gone
        val days = if (pending == null) model.days else model.days
            .map { day -> day.copy(items = day.items.filterNot { pending.hides(it.transaction) }) }
            .filter { it.items.isNotEmpty() }
        CategoryDetailUiState.Ready(
            model = model.copy(days = days),
            filterAccounts = allAccounts.filter { it.id in model.accountIds },
            selectedAccount = account.value,
            sort = sort.value,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryDetailUiState.Loading)

    fun onMonthChange(newMonth: YearMonth) {
        month.value = newMonth
    }

    fun onAccountFilter(id: AccountId?) {
        account.value = id
    }

    fun onSortChange(order: SortOrder) {
        sort.value = order
    }
}
