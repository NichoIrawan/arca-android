package org.nicho.arca.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.nicho.arca.data.db.entities.AccountEntity

@Dao
internal interface AccountDao {

    @Insert
    suspend fun insert(account: AccountEntity)

    @Insert
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Update
    suspend fun update(account: AccountEntity): Int

    @Query("UPDATE accounts SET isArchived = 1, updatedAt = :now WHERE id = :id")
    suspend fun archive(id: String, now: Long): Int

    // Throws SQLiteConstraintException when the account still has transactions (FK RESTRICT,
    // FR-203). The store turns that into DomainError.AccountHasTransactions.
    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Query(
        """
        SELECT * FROM accounts
        WHERE :includeArchived OR isArchived = 0
        ORDER BY sortOrder ASC, name ASC
        """
    )
    fun observeAll(includeArchived: Boolean): Flow<List<AccountEntity>>

    @Query(
        """
        SELECT * FROM accounts
        WHERE isFavorite = 1 AND isArchived = 0
        ORDER BY sortOrder ASC, name ASC
        """
    )
    fun observeFavorites(): Flow<List<AccountEntity>>

    // Checks both sides of a transfer: an account that only ever received transfers still has
    // history that RESTRICT protects.
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM transactions
            WHERE accountId = :id OR counterAccountId = :id
        )
        """
    )
    suspend fun hasTransactions(id: String): Boolean
}
