package org.nicho.arca.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import org.nicho.arca.data.db.dao.AccountDao
import org.nicho.arca.data.db.dao.BudgetAllocationDao
import org.nicho.arca.data.db.dao.CategoryDao
import org.nicho.arca.data.db.dao.DemoDataDao
import org.nicho.arca.data.db.dao.TransactionDao
import org.nicho.arca.data.db.entities.AccountEntity
import org.nicho.arca.data.db.entities.BudgetAllocationEntity
import org.nicho.arca.data.db.entities.CategoryEntity
import org.nicho.arca.data.db.entities.TransactionEntity

// SDD-ARC-002 §6.5. Version 1 is the first schema that ships, envelopes included.
// From the first release on, every schema change ships a Migration and a MigrationTestHelper
// test. fallbackToDestructiveMigration is forbidden: a money app must never wipe its ledger
// because a migration failed.
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetAllocationEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
internal abstract class ArcaDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetAllocationDao(): BudgetAllocationDao
    abstract fun demoDataDao(): DemoDataDao

    companion object {
        // Also referenced by the Auto Backup rules in :app (DD-A16).
        const val NAME = "arca.db"
    }
}
