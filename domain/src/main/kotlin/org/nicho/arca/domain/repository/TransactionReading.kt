package org.nicho.arca.domain.repository

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.BalanceRow
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.Period
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionId
import org.nicho.arca.domain.model.TransactionListItem
import org.nicho.arca.domain.model.TransferGroupId

// SDD-ARC-002 §7.2, DD-23. Reads are Flows that re-emit after any committed write (DD-A07).
interface TransactionReading {
    fun observeInPeriod(period: Period): Flow<List<Transaction>>
    fun observeForAccount(id: AccountId): Flow<List<Transaction>>
    fun observeRecent(limit: Int): Flow<List<TransactionListItem>>
    fun observeBalanceRows(): Flow<List<BalanceRow>>
    /** Whether there is any transaction at all: cheaper than reading every row to find out. */
    fun observeHasAny(): Flow<Boolean>
    suspend fun byId(id: TransactionId): Transaction?
    fun observeById(id: TransactionId): Flow<Transaction?>
    fun observeGroup(groupId: TransferGroupId): Flow<List<Transaction>>
    /** How many transactions each category has, all time (a count, not money). */
    fun observeCountsByCategory(): Flow<Map<CategoryId, Int>>
    /** The oldest transaction's date, or null with none: where "data available since" starts. */
    fun observeEarliestDate(): Flow<Instant?>
    suspend fun allForExport(): List<Transaction>
    suspend fun count(): Int
}
