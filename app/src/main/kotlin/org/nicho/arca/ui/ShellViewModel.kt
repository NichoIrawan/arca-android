package org.nicho.arca.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import org.nicho.arca.undo.PendingDeletion
import org.nicho.arca.undo.UndoCoordinator

/** What the shell itself shows: the undo snackbar for a pending deletion (DD-A11). */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val undoCoordinator: UndoCoordinator,
) : ViewModel() {
    val pendingDeletion: StateFlow<PendingDeletion?> = undoCoordinator.pending

    fun undo() = undoCoordinator.undo()
}
