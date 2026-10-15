package org.nicho.arca.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.nicho.arca.data.db.entities.CategoryEntity

// Guarded writes return the number of rows changed. 0 means the guard refused (Fees, or an
// income category), and the store turns that into a DomainError. The guard is defence in depth
// behind the domain rules, the same posture as FK RESTRICT behind FR-203 (SDD-ARC-002 §8.1, §8.7).
@Dao
internal interface CategoryDao {

    @Insert
    suspend fun insert(category: CategoryEntity)

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    // Appearance only. Not @Update: a whole-row write would also overwrite budgetMinor, which
    // must change only together with its allocation row (RoomBudgetStore), and kind, which
    // never changes (DD-A21).
    @Query(
        """
        UPDATE categories SET name = :name, icon = :icon, colorHex = :colorHex, updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun updateDetails(id: String, name: String, icon: String, colorHex: String, now: Long): Int

    @Query("UPDATE categories SET isArchived = 1, updatedAt = :now WHERE id = :id")
    suspend fun archive(id: String, now: Long): Int

    @Query("UPDATE categories SET isArchived = 0, updatedAt = :now WHERE id = :id")
    suspend fun unarchive(id: String, now: Long): Int

    // Fees can be archived but never deleted (FR-250). A category with transactions also throws
    // here, via FK RESTRICT.
    @Query("DELETE FROM categories WHERE id = :id AND isSystemLocked = 0")
    suspend fun delete(id: String): Int

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query(
        """
        SELECT * FROM categories
        WHERE :includeArchived OR isArchived = 0
        ORDER BY name ASC
        """
    )
    fun observeAll(includeArchived: Boolean): Flow<List<CategoryEntity>>

    // Fees. Found by the flag, never by name: names are renameable and localised (SRS §6.1 v1.6).
    @Query("SELECT * FROM categories WHERE isSystemLocked = 1 LIMIT 1")
    suspend fun getSystemLocked(): CategoryEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM categories WHERE isSystemLocked = 1)")
    suspend fun hasSystemLocked(): Boolean

    @Query(
        """
        UPDATE categories SET budgetMinor = :amountMinor, updatedAt = :now
        WHERE id = :id AND isSystemLocked = 0 AND kind = 'expense'
        """
    )
    suspend fun setBudgetMinor(id: String, amountMinor: Long?, now: Long): Int

    @Query(
        """
        UPDATE categories SET rollsOver = :rollsOver, updatedAt = :now
        WHERE id = :id AND isSystemLocked = 0 AND kind = 'expense'
        """
    )
    suspend fun setRollsOver(id: String, rollsOver: Boolean, now: Long): Int
}
