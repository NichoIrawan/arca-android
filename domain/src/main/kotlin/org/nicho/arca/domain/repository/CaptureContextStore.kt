package org.nicho.arca.domain.repository

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import org.nicho.arca.domain.model.AccountId

// SDD-ARC-002 §8.6 · FR-104: after a save, the next capture starts on the same account and
// date. An interface here so CaptureViewModel depends on :domain only, like every other store.
interface CaptureContextStore {
    val context: Flow<CaptureContext>
    suspend fun remember(accountId: AccountId, date: Instant)
}

data class CaptureContext(
    val lastAccountId: AccountId?,
    val lastDate: Instant?,
)
