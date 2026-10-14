package org.nicho.arca.data

import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nicho.arca.data.db.ArcaDatabase

// SDD-ARC-002 §14.2 "Atomic insert rollback" (DD-A09, FR-131, RSK-07): a transfer is written
// whole or not at all. This tests the mechanism RoomTransactionStore.insertAtomic will use.
@RunWith(AndroidJUnit4::class)
class AtomicInsertTest {

    private lateinit var db: ArcaDatabase

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        db.accountDao().insertAll(listOf(account("bca"), account("gopay")))
        db.categoryDao().insert(category("fees", isSystemLocked = true))
    }

    @After
    fun tearDown() = db.close()

    private val transfer = listOf(
        transaction("out", "bca", type = "transferOut", categoryId = null, transferGroupId = "g1", counterAccountId = "gopay"),
        transaction("in", "gopay", type = "transferIn", categoryId = null, transferGroupId = "g1", counterAccountId = "bca"),
        transaction("fee", "bca", amountMinor = 2_500, categoryId = "fees", transferGroupId = "g1"),
    )

    @Test
    fun aThrowMidTransaction_leavesZeroRows() = runTest {
        val result = runCatching {
            db.withTransaction {
                db.transactionDao().insertAll(transfer.take(2))
                error("simulated failure before the fee row")
            }
        }

        assertTrue(result.isFailure)
        assertEquals(0, db.transactionDao().count())
    }

    @Test
    fun aFailingRowInsideTheBatch_rollsBackTheOthers() = runTest {
        val broken = transfer + transaction("bad", "missing-account", categoryId = null)

        val result = runCatching { db.withTransaction { db.transactionDao().insertAll(broken) } }

        assertTrue(result.isFailure)
        assertEquals(0, db.transactionDao().count())
    }

    @Test
    fun aCompleteTransfer_commitsAllThreeMembers() = runTest {
        db.withTransaction { db.transactionDao().insertAll(transfer) }

        assertEquals(3, db.transactionDao().inGroup("g1").size)
    }
}
