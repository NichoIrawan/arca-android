package org.nicho.arca.data

import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.nicho.arca.data.repository.RoomCategoryStore
import org.nicho.arca.domain.error.DomainError
import org.nicho.arca.domain.model.CategoryKind

// SDD-ARC-002 §8.7 · FR-250.
@RunWith(AndroidJUnit4::class)
class SeedDefaultsTest {

    private lateinit var db: ArcaDatabase
    private lateinit var store: RoomCategoryStore

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        store = RoomCategoryStore(db, db.categoryDao(), TestClock())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun seedsTheDefaults_withExactlyOneLockedFeesCategory() = runTest {
        store.seedDefaultsIfNeeded()

        val all = store.observeAll(includeArchived = true).first()
        assertEquals(10, all.size)
        assertEquals(8, all.count { it.kind == CategoryKind.EXPENSE })
        assertEquals(setOf("Gaji", "Pemasukan Lainnya"), all.filter { it.kind == CategoryKind.INCOME }.map { it.name }.toSet())
        val fees = all.single { it.isSystemLocked }
        assertEquals(CategoryKind.EXPENSE, fees.kind)
        assertEquals(fees, store.feesCategory())
        assertTrue(all.all { it.budgetMinor == null && !it.rollsOver }) // nothing starts budgeted
    }

    @Test
    fun secondRun_seedsNothing() = runTest {
        store.seedDefaultsIfNeeded()
        store.seedDefaultsIfNeeded()

        assertEquals(10, store.observeAll(includeArchived = true).first().size)
    }

    // Idempotence is read from the data: a renamed or archived default still counts.
    @Test
    fun archivedAndRenamedFees_stillCountsAsSeeded() = runTest {
        store.seedDefaultsIfNeeded()
        val fees = store.feesCategory()
        store.update(fees.copy(name = "Fees"))
        store.archive(fees.id)

        store.seedDefaultsIfNeeded()

        assertEquals(10, store.observeAll(includeArchived = true).first().size)
    }

    @Test
    fun feesCategory_onAnUnseededDatabase_isFeesCategoryMissing() = runTest {
        assertFails<DomainError.FeesCategoryMissing> { store.feesCategory() }
        assertNull(db.categoryDao().getSystemLocked())
    }
}
