package org.nicho.arca.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nicho.arca.data.db.ArcaDatabase

// FR-265: one allocation row per (category, month); a later month adds a row, the same month
// replaces the amount in place.
@RunWith(AndroidJUnit4::class)
class BudgetAllocationDaoTest {

    private lateinit var db: ArcaDatabase

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        db.categoryDao().insert(category("transport"))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun secondEditInTheSameMonth_replacesTheAmountAndKeepsTheRow() = runTest {
        val dao = db.budgetAllocationDao()
        dao.upsertForMonth(allocation("a1", "transport", "2026-09", 1_000_000, at = T0))
        dao.upsertForMonth(allocation("a2", "transport", "2026-09", 1_300_000, at = T0 + 1))

        val rows = dao.forCategory("transport")
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals("a1", row.id)                 // stable id for sync
        assertEquals(1_300_000L, row.amountMinor)
        assertEquals(T0, row.createdAt)
        assertEquals(T0 + 1, row.updatedAt)
    }

    @Test
    fun aLaterMonth_addsARowAndKeepsHistory() = runTest {
        val dao = db.budgetAllocationDao()
        dao.upsertForMonth(allocation("a1", "transport", "2026-07", 1_000_000))
        dao.upsertForMonth(allocation("a2", "transport", "2026-09", 800_000))

        assertEquals(
            listOf("2026-07" to 1_000_000L, "2026-09" to 800_000L),
            dao.observeAll().first().map { it.month to it.amountMinor },
        )
    }

    @Test
    fun aNullAmount_recordsTheRemoval() = runTest {
        val dao = db.budgetAllocationDao()
        dao.upsertForMonth(allocation("a1", "transport", "2026-09", 500_000))
        dao.upsertForMonth(allocation("a2", "transport", "2026-10", null))

        assertNull(dao.forCategory("transport").last().amountMinor)
    }

    @Test
    fun deletingTheCategory_cascadesToItsAllocations() = runTest {
        db.budgetAllocationDao().upsertForMonth(allocation("a1", "transport", "2026-09", 500_000))

        db.categoryDao().delete("transport")

        assertEquals(0, db.budgetAllocationDao().forCategory("transport").size)
    }
}
