package org.nicho.arca.feature.reports

import org.nicho.arca.domain.readmodel.ReportMetric
import org.nicho.arca.domain.readmodel.ReportsModel

sealed interface ReportsUiState {
    data object Loading : ReportsUiState
    data class Ready(val model: ReportsModel, val metric: ReportMetric = ReportMetric.EXPENSES) : ReportsUiState
}
