package org.nicho.arca.domain.repository

import kotlinx.coroutines.flow.Flow
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.BalanceRow
import org.nicho.arca.domain.model.Period
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionId
import org.nicho.arca.domain.model.TransactionListItem

// SDD-ARC-002 §7.2, DD-23. Reads are Flows that re-emit after any committed write (DD-A07).
interface TransactionReading {
    fun observeInPeriod(period: Period): Flow<List<Transaction>>
    fun observeForAccount(id: AccountId): Flow<List<Transaction>>
    fun observeRecent(limit: Int): Flow<List<TransactionListItem>>
    fun observeBalanceRows(): Flow<List<BalanceRow>>
    suspend fun byId(id: TransactionId): Transaction?
    suspend fun allForExport(): List<Transaction>
    suspend fun count(): Int
}
