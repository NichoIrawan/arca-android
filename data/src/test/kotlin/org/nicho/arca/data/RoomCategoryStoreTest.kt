package org.nicho.arca.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nicho.arca.data.db.ArcaDatabase
import org.nicho.arca.data.repository.RoomAccountStore
import org.nicho.arca.data.repository.RoomBudgetStore
import org.nicho.arca.data.repository.RoomCategoryStore
import org.nicho.arca.data.repository.RoomTransactionStore
import org.nicho.arca.domain.error.DomainError
import org.nicho.arca.domain.error.PersistenceError
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.CategoryKind
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class RoomCategoryStoreTest {

    private lateinit var db: ArcaDatabase
    private lateinit var clock: TestClock
    private lateinit var store: RoomCategoryStore

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        clock = TestClock()
        store = RoomCategoryStore(db, db.categoryDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun update_writesAppearanceOnly_neverKindOrBudget() = runTest {
        val food = domainCategory(name = "Makan")
        store.insert(food)
        RoomBudgetStore(db, db.budgetAllocationDao(), db.categoryDao(), clock)
            .setBudget(food.id, YearMonth.of(2026, 9), 1_000_000)

        store.update(
            food.copy(
                name = "Makanan",
                icon = "soup",
                colorHex = "#069f89",
                kind = CategoryKind.INCOME, // ignored: immutable (DD-A21)
                budgetMinor = 1,            // ignored: BudgetStore's field
            )
        )

        val stored = store.byId(food.id)!!
        assertEquals("Makanan", stored.name)
        assertEquals("soup", stored.icon)
        assertEquals("#069f89", stored.colorHex)
        assertEquals(CategoryKind.EXPENSE, stored.kind)
        assertEquals(1_000_000L, stored.budgetMinor)
    }

    @Test
    fun update_ofAMissingCategory_fails() = runTest {
        assertFails<PersistenceError.SaveFailed> { store.update(domainCategory()) }
    }

    @Test
    fun insert_withABudget_isRejected() = runTest {
        assertFails<IllegalArgumentException> {
            store.insert(domainCategory().copy(budgetMinor = 500_000))
        }
    }

    @Test
    fun insert_systemLocked_isRejected() = runTest {
        assertFails<IllegalArgumentException> {
            store.insert(domainCategory().copy(isSystemLocked = true))
        }
    }

    @Test
    fun delete_fees_isCategoryIsSystemLocked() = runTest {
        store.seedDefaultsIfNeeded()
        val fees = store.feesCategory()

        assertFails<DomainError.CategoryIsSystemLocked> { store.delete(fees.id) }

        assertNotNull(store.byId(fees.id))
    }

    @Test
    fun delete_withTransactions_isCategoryHasTransactions() = runTest {
        val food = domainCategory()
        val account = domainAccount()
        store.insert(food)
        RoomAccountStore(db, db.accountDao(), clock).insert(account)
        RoomTransactionStore(db, db.transactionDao(), clock).insert(domainTransaction(account.id, food.id))

        assertFails<DomainError.CategoryHasTransactions> { store.delete(food.id) }

        assertNotNull(store.byId(food.id))
    }

    @Test
    fun delete_unusedCategory_removesIt_andAMissingOneIsANoOp() = runTest {
        val food = domainCategory()
        store.insert(food)

        store.delete(food.id)
        store.delete(CategoryId.random())

        assertNull(store.byId(food.id))
    }
}
