package org.nicho.arca.data

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nicho.arca.data.db.ArcaDatabase

// SDD-ARC-002 §14.2 "FK enforcement": proves RESTRICT is live rather than trusting that Room
// turned foreign keys on.
@RunWith(AndroidJUnit4::class)
class ForeignKeyEnforcementTest {

    private lateinit var db: ArcaDatabase

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        db.accountDao().insertAll(listOf(account("bca"), account("gopay")))
        db.categoryDao().insert(category("food"))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun deletingAnAccountWithTransactions_throwsAndKeepsBoth() = runTest {
        db.transactionDao().insert(transaction("t1", "bca"))

        assertFails<SQLiteConstraintException> {
            db.accountDao().delete("bca")
        }

        assertNotNull(db.accountDao().getById("bca"))
        assertEquals(1, db.transactionDao().count())
    }

    @Test
    fun deletingAnAccountThatOnlyReceivedTransfers_throws() = runTest {
        db.transactionDao().insert(
            transaction("in", "bca", type = "transferIn", categoryId = null, transferGroupId = "g1", counterAccountId = "gopay")
        )

        assertTrue(db.accountDao().hasTransactions("gopay"))
        assertFails<SQLiteConstraintException> {
            db.accountDao().delete("gopay")
        }
    }

    @Test
    fun deletingAnAccountWithoutTransactions_succeeds() = runTest {
        assertFalse(db.accountDao().hasTransactions("gopay"))

        assertEquals(1, db.accountDao().delete("gopay"))
    }

    @Test
    fun deletingACategoryWithTransactions_throws() = runTest {
        db.transactionDao().insert(transaction("t1", "bca", categoryId = "food"))

        assertFails<SQLiteConstraintException> {
            db.categoryDao().delete("food")
        }
    }

    @Test
    fun insertingATransactionForAMissingAccount_throws() = runTest {
        assertFails<SQLiteConstraintException> {
            db.transactionDao().insert(transaction("t1", "nope"))
        }
    }
}
