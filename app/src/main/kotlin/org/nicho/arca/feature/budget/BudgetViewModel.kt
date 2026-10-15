package org.nicho.arca.feature.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.nicho.arca.domain.readmodel.ObserveBudget

/**
 * The Budget tab (SDD-ARC-002 §9.12): holds the selected month and maps the read model. Every
 * figure, the "Over by" amount included, comes from EnvelopeCalculator; this formats only.
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(observeBudget: ObserveBudget) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())

    val state: StateFlow<BudgetUiState> = observeBudget(month)
        .map { BudgetUiState.Ready(it.summary.month, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState.Loading)

    fun onMonthChange(newMonth: YearMonth) {
        month.value = newMonth
    }
}
