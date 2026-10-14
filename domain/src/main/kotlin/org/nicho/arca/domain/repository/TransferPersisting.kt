package org.nicho.arca.domain.repository

import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransferGroupId

// SDD-ARC-002 §7.2, DD-A09 · FR-131. All members of a transfer are written in one transaction:
// all of them or none.
interface TransferPersisting {
    suspend fun insertAtomic(txs: List<Transaction>)
    suspend fun deleteAll(groupId: TransferGroupId)
    suspend fun inGroup(groupId: TransferGroupId): List<Transaction>
}
