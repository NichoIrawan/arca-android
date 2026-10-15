package org.nicho.arca.feature.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.nicho.arca.domain.readmodel.ObserveSummary
import org.nicho.arca.undo.UndoCoordinator

/** The Overview tab, always the current month (DD-22). Maps the read model; never sums. */
@HiltViewModel
class SummaryViewModel @Inject constructor(
    observeSummary: ObserveSummary,
    undo: UndoCoordinator,
) : ViewModel() {
    val state: StateFlow<SummaryUiState> =
        combine(observeSummary(), undo.pending) { model, pending ->
            // Rows waiting out their undo window are hidden at once (DD-A11).
            SummaryUiState.Ready(model.copy(recent = model.recent.filterNot { pending?.hides(it.transaction) == true }))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState.Loading)
}
