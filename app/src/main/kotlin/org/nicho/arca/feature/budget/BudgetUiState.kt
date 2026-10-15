package org.nicho.arca.feature.budget

import java.time.YearMonth
import org.nicho.arca.domain.readmodel.BudgetModel

sealed interface BudgetUiState {
    data object Loading : BudgetUiState
    data class Ready(val month: YearMonth, val model: BudgetModel) : BudgetUiState {
        /** The month in progress; the navigator goes one month past it, to the one not yet started. */
        val currentMonth: YearMonth get() = YearMonth.from(model.today)
    }
}
