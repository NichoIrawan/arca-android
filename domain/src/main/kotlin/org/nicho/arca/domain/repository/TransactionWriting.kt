package org.nicho.arca.domain.repository

import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionId

// SDD-ARC-002 §7.2. Single-row writes. Anything that must land together goes through
// TransferPersisting.insertAtomic.
interface TransactionWriting {
    suspend fun insert(tx: Transaction)
    suspend fun update(tx: Transaction)
    suspend fun delete(ids: List<TransactionId>)
}
