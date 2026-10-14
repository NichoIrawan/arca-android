package org.nicho.arca.domain.repository

import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransferGroupId

// SDD-ARC-002 §7.2, DD-A09 · FR-131. Every write here is one transaction: all members land, or
// none do.
interface TransferPersisting {
    suspend fun insertAtomic(txs: List<Transaction>)

    /**
     * Replaces every member of [groupId] with [txs] in one transaction: an edit. A separate
     * delete and insert would leave the transfer half-gone if the process died between them.
     */
    suspend fun replaceGroup(groupId: TransferGroupId, txs: List<Transaction>)

    suspend fun deleteAll(groupId: TransferGroupId)
    suspend fun inGroup(groupId: TransferGroupId): List<Transaction>
}
