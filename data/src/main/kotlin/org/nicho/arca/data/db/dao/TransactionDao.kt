package org.nicho.arca.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.nicho.arca.data.db.entities.BalanceRowEntity
import org.nicho.arca.data.db.entities.TransactionEntity
import org.nicho.arca.data.db.entities.TransactionListItemRow

// SDD-ARC-002 §8.1. No query here sums, totals or averages an amount (DD-A10, CI grep §14.3).
// Each single call is its own implicit transaction; multi-row writes that must land together
// go through ArcaDatabase.withTransaction in the store (DD-A09).
@Dao
internal interface TransactionDao {

    @Insert
    suspend fun insert(tx: TransactionEntity)

    @Insert
    suspend fun insertAll(txs: List<TransactionEntity>)

    @Update
    suspend fun update(tx: TransactionEntity): Int

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    // Matches the fee row as well as both principals, so a fee is never orphaned (FR-131, FR-133).
    @Query("DELETE FROM transactions WHERE transferGroupId = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE transferGroupId = :groupId")
    suspend fun inGroup(groupId: String): List<TransactionEntity>

    // Half-open [from, to): a transaction at exactly `to` belongs to the next period.
    @Query(
        """
        SELECT * FROM transactions
        WHERE date >= :from AND date < :to
        ORDER BY date DESC, createdAt DESC
        """
    )
    fun observeInPeriod(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE accountId = :accountId
        ORDER BY date DESC, createdAt DESC
        """
    )
    fun observeForAccount(accountId: String): Flow<List<TransactionEntity>>

    @Query("SELECT accountId, amountMinor, type FROM transactions")
    fun observeBalanceRows(): Flow<List<BalanceRowEntity>>

    // Display fields only; no arithmetic. LEFT JOINs because transfers have no category and
    // only transfer principals have a counter account.
    @Query(
        """
        SELECT t.*,
               a.name      AS accountName,
               a.colorHex  AS accountColor,
               ca.name     AS counterAccountName,
               c.name      AS categoryName,
               c.icon      AS categoryIcon,
               c.colorHex  AS categoryColor
        FROM transactions t
        JOIN accounts a       ON a.id  = t.accountId
        LEFT JOIN accounts ca ON ca.id = t.counterAccountId
        LEFT JOIN categories c ON c.id = t.categoryId
        ORDER BY t.date DESC, t.createdAt DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int): Flow<List<TransactionListItemRow>>

    @Query("SELECT * FROM transactions ORDER BY date ASC, createdAt ASC")
    suspend fun getAll(): List<TransactionEntity>

    // A row count, not money, so it's outside the no-aggregates rule.
    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}
