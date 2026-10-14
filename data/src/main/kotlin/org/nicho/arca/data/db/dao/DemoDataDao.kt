package org.nicho.arca.data.db.dao

import androidx.room.Dao
import androidx.room.Query

// Only DemoDataSeeder uses this. The wipe queries live in their own DAO so that no store gets
// "delete everything" sitting in its autocomplete.
@Dao
internal interface DemoDataDao {

    // Categories don't count: every install has the seeded defaults.
    @Query(
        """
        SELECT EXISTS(SELECT 1 FROM accounts)
            OR EXISTS(SELECT 1 FROM transactions)
        """
    )
    suspend fun hasUserData(): Boolean

    // Children first, so no FK RESTRICT fires.
    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("DELETE FROM budget_allocations")
    suspend fun deleteAllAllocations()

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()
}
