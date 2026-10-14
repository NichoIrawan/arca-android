package org.nicho.arca.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nicho.arca.data.db.ArcaDatabase

// The guarded writes of SDD-ARC-002 §8.1/§8.7: Fees and income categories can't be budgeted,
// Fees can't be deleted. Each guard reports refusal as 0 rows changed.
@RunWith(AndroidJUnit4::class)
class CategoryDaoTest {

    private lateinit var db: ArcaDatabase

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        db.categoryDao().insertAll(
            listOf(
                category("food"),
                category("fees", isSystemLocked = true),
                category("salary", kind = "income"),
            )
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun setBudgetMinor_onAnExpenseCategory_writes() = runTest {
        assertEquals(1, db.categoryDao().setBudgetMinor("food", 1_300_000, T0 + 1))

        val food = db.categoryDao().getById("food")!!
        assertEquals(1_300_000L, food.budgetMinor)
        assertEquals(T0 + 1, food.updatedAt)
    }

    @Test
    fun setBudgetMinor_onFees_isRefused() = runTest {
        assertEquals(0, db.categoryDao().setBudgetMinor("fees", 50_000, T0 + 1))
        assertNull(db.categoryDao().getById("fees")!!.budgetMinor)
    }

    @Test
    fun setBudgetMinor_onAnIncomeCategory_isRefused() = runTest {
        assertEquals(0, db.categoryDao().setBudgetMinor("salary", 50_000, T0 + 1))
    }

    @Test
    fun setRollsOver_followsTheSameGuard() = runTest {
        assertEquals(1, db.categoryDao().setRollsOver("food", true, T0 + 1))
        assertEquals(0, db.categoryDao().setRollsOver("fees", true, T0 + 1))
        assertEquals(0, db.categoryDao().setRollsOver("salary", true, T0 + 1))
    }

    @Test
    fun delete_refusesFeesButAllowsArchive() = runTest {
        assertEquals(0, db.categoryDao().delete("fees"))
        assertNotNull(db.categoryDao().getById("fees"))

        assertEquals(1, db.categoryDao().archive("fees", T0 + 1))
        assertTrue(db.categoryDao().getById("fees")!!.isArchived)
    }

    @Test
    fun systemLockedCategory_isFoundByFlag() = runTest {
        assertTrue(db.categoryDao().hasSystemLocked())
        assertEquals("fees", db.categoryDao().getSystemLocked()!!.id)
    }

    @Test
    fun emptyDatabase_hasNoSystemLockedCategory() = runTest {
        val empty = inMemoryDatabase()
        assertFalse(empty.categoryDao().hasSystemLocked())
        empty.close()
    }
}
