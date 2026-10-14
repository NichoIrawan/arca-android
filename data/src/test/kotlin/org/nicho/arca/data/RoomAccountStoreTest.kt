package org.nicho.arca.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nicho.arca.data.db.ArcaDatabase
import org.nicho.arca.data.mapper.key
import org.nicho.arca.data.repository.RoomAccountStore
import org.nicho.arca.data.repository.RoomCategoryStore
import org.nicho.arca.data.repository.RoomTransactionStore
import org.nicho.arca.domain.error.DomainError
import org.nicho.arca.domain.error.PersistenceError
import org.nicho.arca.domain.model.AccountId

@RunWith(AndroidJUnit4::class)
class RoomAccountStoreTest {

    private lateinit var db: ArcaDatabase
    private lateinit var clock: TestClock
    private lateinit var store: RoomAccountStore

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        clock = TestClock()
        store = RoomAccountStore(db, db.accountDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun update_keepsCreatedAt() = runTest {
        val account = domainAccount(name = "BCA")
        store.insert(account)

        clock.millis = T0 + 1
        store.update(account.copy(name = "BCA Utama"))

        val row = db.accountDao().getById(account.id.key())!!
        assertEquals("BCA Utama", row.name)
        assertEquals(T0, row.createdAt)
        assertEquals(T0 + 1, row.updatedAt)
    }

    @Test
    fun archive_hidesFromTheDefaultList() = runTest {
        val account = domainAccount()
        store.insert(account)

        store.archive(account.id)

        assertEquals(emptyList<Any>(), store.observeAll(includeArchived = false).first())
        assertTrue(store.observeAll(includeArchived = true).first().single().isArchived)
    }

    @Test
    fun archive_ofAMissingAccount_fails() = runTest {
        assertFails<PersistenceError.SaveFailed> { store.archive(AccountId.random()) }
    }

    @Test
    fun observeFavorites_returnsOnlyFavourites() = runTest {
        val favourite = domainAccount(name = "GoPay", isFavorite = true)
        store.insert(favourite)
        store.insert(domainAccount(name = "BNI"))

        assertEquals(listOf(favourite), store.observeFavorites().first())
    }

    // FR-203, SDD-ARC-002 §8.3: the FK refusal arrives as the blocked-action domain error.
    @Test
    fun delete_withTransactions_isAccountHasTransactions_andKeepsTheAccount() = runTest {
        val account = domainAccount()
        val food = domainCategory()
        store.insert(account)
        RoomCategoryStore(db, db.categoryDao(), clock).insert(food)
        RoomTransactionStore(db, db.transactionDao(), clock).insert(domainTransaction(account.id, food.id))

        assertFails<DomainError.AccountHasTransactions> { store.delete(account.id) }

        assertNotNull(store.byId(account.id))
        assertTrue(store.hasTransactions(account.id))
    }

    @Test
    fun delete_withoutTransactions_removesIt() = runTest {
        val account = domainAccount()
        store.insert(account)

        store.delete(account.id)

        assertNull(store.byId(account.id))
    }
}
