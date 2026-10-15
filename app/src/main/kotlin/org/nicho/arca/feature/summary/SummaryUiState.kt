package org.nicho.arca.feature.summary

import org.nicho.arca.domain.readmodel.SummaryModel

sealed interface SummaryUiState {
    /** Skeleton while the first fold runs, so the screen is interactive at once (SDD-ARC-002 §9.4). */
    data object Loading : SummaryUiState
    data class Ready(val model: SummaryModel) : SummaryUiState
}
