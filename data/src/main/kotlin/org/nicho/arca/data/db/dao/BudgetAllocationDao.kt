package org.nicho.arca.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.nicho.arca.data.db.entities.BudgetAllocationEntity

@Dao
internal interface BudgetAllocationDao {

    @Query("SELECT * FROM budget_allocations ORDER BY categoryId ASC, month ASC")
    fun observeAll(): Flow<List<BudgetAllocationEntity>>

    @Query("SELECT * FROM budget_allocations WHERE categoryId = :categoryId ORDER BY month ASC")
    suspend fun forCategory(categoryId: String): List<BudgetAllocationEntity>

    @Insert
    suspend fun insert(allocation: BudgetAllocationEntity)

    @Query(
        """
        UPDATE budget_allocations SET amountMinor = :amountMinor, updatedAt = :now
        WHERE categoryId = :categoryId AND month = :month
        """
    )
    suspend fun updateAmount(categoryId: String, month: String, amountMinor: Long?, now: Long): Int

    /**
     * Writes the budget for (categoryId, month): a second edit in the same month replaces that
     * month's amount instead of adding a row (FR-265).
     *
     * Not Room's @Upsert, which the SDD sketches: @Upsert falls back to an update by *primary key*.
     * A new allocation carries a fresh id, so when the month already has a row the insert hits the
     * unique (categoryId, month) index and the fallback update matches nothing. The edit would be
     * silently dropped. Matching on (categoryId, month) also keeps the existing row's id and
     * createdAt, which sync needs to stay stable.
     */
    @Transaction
    suspend fun upsertForMonth(allocation: BudgetAllocationEntity) {
        val updated = updateAmount(
            categoryId = allocation.categoryId,
            month = allocation.month,
            amountMinor = allocation.amountMinor,
            now = allocation.updatedAt,
        )
        if (updated == 0) insert(allocation)
    }
}
