package org.nicho.arca.undo

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nicho.arca.di.ApplicationScope
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionId
import org.nicho.arca.domain.model.TransferGroupId
import org.nicho.arca.domain.repository.TransactionWriting
import org.nicho.arca.domain.transfer.TransferService
import kotlin.time.Duration.Companion.milliseconds

/** A deletion waiting out its undo window. */
data class PendingDeletion(
    val ids: Set<TransactionId>,
    val transferGroupId: TransferGroupId?, // set when a whole transfer group is going
    val token: Long,
) {
    val memberCount: Int get() = ids.size

    /** Whether a list should hide [tx] while this deletion waits: the row itself, or any member of its transfer. */
    fun hides(tx: Transaction): Boolean =
        tx.id in ids || (transferGroupId != null && tx.transferGroupId == transferGroupId)
}

/**
 * SDD-ARC-002 §9.7, DD-A11 · FR-106. Deletion is deferred: rows vanish from lists at once, but the
 * store is touched only when the 5-second window closes. Undo cancels the timer, so nothing ever
 * changed.
 *
 * Lives in the application scope, so rotating the screen doesn't cancel a pending commit. If the
 * process dies inside the window, the deletion never happened: a money app fails toward keeping
 * data.
 */
@Singleton
class UndoCoordinator @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val writing: TransactionWriting,
    private val transfers: TransferService,
) {
    private val _pending = MutableStateFlow<PendingDeletion?>(null)
    val pending: StateFlow<PendingDeletion?> = _pending.asStateFlow()

    private var timer: Job? = null
    private var nextToken = 0L

    /** Deletes one income or expense row. */
    fun deleteTransaction(id: TransactionId) = schedule(PendingDeletion(setOf(id), null, nextToken++))

    /** Deletes every member of a transfer, its fee included (FR-131). */
    fun deleteTransfer(groupId: TransferGroupId) {
        scope.launch {
            val members = transfers.members(groupId).map { it.id }.toSet()
            schedule(PendingDeletion(members, groupId, nextToken++))
        }
    }

    fun undo() {
        timer?.cancel()
        timer = null
        _pending.value = null
    }

    private fun schedule(deletion: PendingDeletion) {
        // A second delete inside the window commits the first one now rather than dropping it.
        _pending.value?.let { previous ->
            timer?.cancel()
            scope.launch { commit(previous) }
        }
        _pending.value = deletion
        timer = scope.launch {
            delay(WINDOW_MILLIS.milliseconds)
            commit(deletion)
            if (_pending.value?.token == deletion.token) _pending.value = null
        }
    }

    private suspend fun commit(deletion: PendingDeletion) {
        val group = deletion.transferGroupId
        if (group != null) transfers.deleteTransfer(group) else writing.delete(deletion.ids.toList())
    }

    companion object {
        const val WINDOW_MILLIS = 5_000L
    }
}
