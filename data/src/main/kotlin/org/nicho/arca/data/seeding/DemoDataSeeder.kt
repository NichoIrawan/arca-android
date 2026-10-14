package org.nicho.arca.data.seeding

import androidx.room.withTransaction
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import org.nicho.arca.data.db.ArcaDatabase
import org.nicho.arca.data.db.entities.TransactionEntity
import org.nicho.arca.data.repository.saveOrFail
import org.nicho.arca.domain.seeding.DemoDataConfig
import org.nicho.arca.domain.seeding.DemoDataSeeding
import org.nicho.arca.domain.seeding.SeedResult

// SDD-ARC-002 §8.5 · FR-902. Reachable only when BuildConfig.DEMO_DATA_ENABLED (§5.3).
internal class DemoDataSeeder @Inject constructor(
    private val db: ArcaDatabase,
    private val clock: Clock,
) : DemoDataSeeding {

    override suspend fun seed(config: DemoDataConfig, replaceExisting: Boolean): SeedResult = saveOrFail {
        val demo = DemoDataGenerator(config, clock.instant(), ZoneId.systemDefault()).generate()
        val dao = db.demoDataDao()

        // The emptiness check, the wipe and the reference data share one transaction, so nothing
        // can be written between "it's empty" and the seed. The demo set includes its own copy of
        // the default categories (Fees too), so the existing ones go: two Fees would break FR-133.
        val started = db.withTransaction {
            if (dao.hasUserData() && !replaceExisting) return@withTransaction false
            dao.deleteAllTransactions()
            dao.deleteAllAllocations()
            dao.deleteAllAccounts()
            dao.deleteAllCategories()
            db.categoryDao().insertAll(demo.categories)
            db.accountDao().insertAll(demo.accounts)
            demo.allocations.forEach { db.budgetAllocationDao().insert(it) }
            true
        }
        if (!started) return SeedResult.DatabaseNotEmpty

        // ~9,000 rows: one commit per row would take minutes on a phone and fire 9,000
        // invalidations; one commit for everything would hold the write lock for the whole seed.
        // Batches are the middle ground. A transfer never straddles two batches.
        demo.transactionGroups.batches(BATCH_ROWS).forEach { batch ->
            db.withTransaction { db.transactionDao().insertAll(batch) }
        }

        SeedResult.Seeded(
            accounts = demo.accounts.size,
            categories = demo.categories.size,
            transactions = demo.transactionCount,
        )
    }

    private fun List<List<TransactionEntity>>.batches(maxRows: Int): List<List<TransactionEntity>> {
        val batches = mutableListOf<List<TransactionEntity>>()
        var current = mutableListOf<TransactionEntity>()
        for (group in this) {
            if (current.isNotEmpty() && current.size + group.size > maxRows) {
                batches += current
                current = mutableListOf()
            }
            current += group
        }
        if (current.isNotEmpty()) batches += current
        return batches
    }

    private companion object {
        const val BATCH_ROWS = 500
    }
}
