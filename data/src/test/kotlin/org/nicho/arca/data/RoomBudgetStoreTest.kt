package org.nicho.arca.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nicho.arca.data.db.ArcaDatabase
import org.nicho.arca.data.db.dao.BudgetAllocationDao
import org.nicho.arca.data.db.entities.BudgetAllocationEntity
import org.nicho.arca.data.repository.RoomBudgetStore
import org.nicho.arca.data.repository.RoomCategoryStore
import org.nicho.arca.domain.error.DomainError
import org.nicho.arca.domain.model.BudgetAllocation
import org.nicho.arca.domain.model.CategoryKind

// SDD-ARC-002 §8.8, §14.2 ("Budget write is atomic", "Fees cannot be budgeted").
@RunWith(AndroidJUnit4::class)
class RoomBudgetStoreTest {

    private lateinit var db: ArcaDatabase
    private lateinit var clock: TestClock
    private lateinit var categories: RoomCategoryStore
    private lateinit var store: RoomBudgetStore

    private val transport = domainCategory(name = "Transportasi")
    private val sep = YearMonth.of(2026, 9)

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        clock = TestClock()
        categories = RoomCategoryStore(db, db.categoryDao(), clock)
        store = RoomBudgetStore(db, db.budgetAllocationDao(), db.categoryDao(), clock)
        categories.insert(transport)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun setBudget_writesTheCurrentBudgetAndTheAllocationTogether() = runTest {
        store.setBudget(transport.id, sep, 1_300_000)

        assertEquals(1_300_000L, categories.byId(transport.id)!!.budgetMinor)
        assertEquals(listOf(BudgetAllocation(transport.id, sep, 1_300_000)), store.observeAllocations().first())
    }

    @Test
    fun aSecondEditInTheSameMonth_replaces_aLaterMonthAdds() = runTest {
        store.setBudget(transport.id, sep, 1_000_000)
        store.setBudget(transport.id, sep, 1_300_000)
        store.setBudget(transport.id, sep.plusMonths(1), null)

        assertEquals(
            listOf(
                BudgetAllocation(transport.id, sep, 1_300_000),
                BudgetAllocation(transport.id, sep.plusMonths(1), null),
            ),
            store.observeAllocations().first(),
        )
        assertNull(categories.byId(transport.id)!!.budgetMinor)
    }

    @Test
    fun fees_isRefusedAtTheStore_andNothingIsWritten() = runTest {
        categories.seedDefaultsIfNeeded()
        val fees = categories.feesCategory()

        assertFails<DomainError.CategoryIsSystemLocked> { store.setBudget(fees.id, sep, 50_000) }
        assertFails<DomainError.CategoryIsSystemLocked> { store.setRollsOver(fees.id, true) }

        assertNull(categories.feesCategory().budgetMinor)
        assertTrue(store.observeAllocations().first().isEmpty())
    }

    @Test
    fun incomeCategory_isCategoryNotBudgetable() = runTest {
        val salary = domainCategory(name = "Gaji", kind = CategoryKind.INCOME)
        categories.insert(salary)

        assertFails<DomainError.CategoryNotBudgeTable> { store.setBudget(salary.id, sep, 1_000_000) }
    }

    // A throw after the category write but before the allocation lands must undo both.
    @Test
    fun aFailureMidWrite_rollsBackTheCategoryChangeToo() = runTest {
        val failingAllocations = object : BudgetAllocationDao by db.budgetAllocationDao() {
            override suspend fun upsertForMonth(allocation: BudgetAllocationEntity) = error("disk on fire")
        }
        val failingStore = RoomBudgetStore(db, failingAllocations, db.categoryDao(), clock)

        assertFails<IllegalStateException> { failingStore.setBudget(transport.id, sep, 1_300_000) }

        assertNull(categories.byId(transport.id)!!.budgetMinor)
        assertTrue(store.observeAllocations().first().isEmpty())
    }

    @Test
    fun setRollsOver_writesTheFlag() = runTest {
        store.setRollsOver(transport.id, true)

        assertTrue(categories.byId(transport.id)!!.rollsOver)
    }
}
