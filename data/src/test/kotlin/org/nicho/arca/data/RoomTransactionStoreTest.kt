package org.nicho.arca.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nicho.arca.data.db.ArcaDatabase
import org.nicho.arca.data.mapper.key
import org.nicho.arca.data.repository.RoomAccountStore
import org.nicho.arca.data.repository.RoomCategoryStore
import org.nicho.arca.data.repository.RoomTransactionStore
import org.nicho.arca.domain.error.PersistenceError
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.Period
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.model.TransferGroupId

@RunWith(AndroidJUnit4::class)
class RoomTransactionStoreTest {

    private lateinit var db: ArcaDatabase
    private lateinit var clock: TestClock
    private lateinit var store: RoomTransactionStore

    private val bca = domainAccount(name = "BCA")
    private val gopay = domainAccount(name = "GoPay")
    private val food = domainCategory()

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        clock = TestClock()
        store = RoomTransactionStore(db, db.transactionDao(), clock)
        val accounts = RoomAccountStore(db, db.accountDao(), clock)
        accounts.insert(bca)
        accounts.insert(gopay)
        RoomCategoryStore(db, db.categoryDao(), clock).insert(food)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertedTransaction_readsBackEqual() = runTest {
        val tx = domainTransaction(bca.id, food.id)

        store.insert(tx)

        assertEquals(tx, store.byId(tx.id))
        assertEquals(listOf(tx), store.allForExport())
    }

    @Test
    fun update_keepsCreatedAt_andMovesUpdatedAt() = runTest {
        val tx = domainTransaction(bca.id, food.id, amountMinor = 50_000)
        store.insert(tx)

        clock.millis = T0 + 60_000
        store.update(tx.copy(amountMinor = 55_000))

        val row = db.transactionDao().getById(tx.id.key())!!
        assertEquals(55_000L, row.amountMinor)
        assertEquals(T0, row.createdAt)
        assertEquals(T0 + 60_000, row.updatedAt)
    }

    @Test
    fun update_ofAMissingTransaction_failsInsteadOfSucceedingSilently() = runTest {
        assertFails<PersistenceError.SaveFailed> {
            store.update(domainTransaction(bca.id, food.id))
        }
    }

    @Test
    fun insert_withAnUnknownAccount_isASaveFailure() = runTest {
        assertFails<PersistenceError.SaveFailed> {
            store.insert(domainTransaction(AccountId.random(), food.id))
        }
    }

    @Test
    fun observeInPeriod_mapsThePeriodToTheHalfOpenQuery() = runTest {
        val start = Instant.ofEpochMilli(T0)
        val end = Instant.ofEpochMilli(T0 + 1_000)
        val inside = domainTransaction(bca.id, food.id, date = start)
        store.insert(inside)
        store.insert(domainTransaction(bca.id, food.id, date = end))

        assertEquals(listOf(inside), store.observeInPeriod(Period(start, end)).first())
    }

    @Test
    fun transfer_withFee_isInsertedAtomically_andDeletedAsAGroup() = runTest {
        val group = TransferGroupId.random()
        val members = listOf(
            domainTransaction(bca.id, null, type = TransactionType.TRANSFER_OUT, amountMinor = 500_000, transferGroupId = group, counterAccountId = gopay.id),
            domainTransaction(gopay.id, null, type = TransactionType.TRANSFER_IN, amountMinor = 500_000, transferGroupId = group, counterAccountId = bca.id),
            domainTransaction(bca.id, food.id, amountMinor = 2_500, transferGroupId = group),
        )

        store.insertAtomic(members)
        assertEquals(members.toSet(), store.inGroup(group).toSet())

        store.deleteAll(group)
        assertEquals(0, store.count())
    }

    @Test
    fun insertAtomic_withOneBadMember_writesNothing() = runTest {
        val group = TransferGroupId.random()
        val members = listOf(
            domainTransaction(bca.id, null, type = TransactionType.TRANSFER_OUT, transferGroupId = group, counterAccountId = gopay.id),
            domainTransaction(AccountId.random(), null, type = TransactionType.TRANSFER_IN, transferGroupId = group, counterAccountId = bca.id),
        )

        assertFails<PersistenceError.SaveFailed> { store.insertAtomic(members) }

        assertEquals(0, store.count())
    }

    @Test
    fun observeRecent_carriesDisplayFields() = runTest {
        val tx = domainTransaction(bca.id, food.id)
        store.insert(tx)

        val item = store.observeRecent(limit = 5).first().single()

        assertEquals(tx, item.transaction)
        assertEquals("BCA", item.accountName)
        assertEquals("Makanan & Minuman", item.categoryName)
        assertNull(item.counterAccountName)
    }

    @Test
    fun observeBalanceRows_mapsTypes() = runTest {
        store.insert(domainTransaction(bca.id, food.id, amountMinor = 10_000))

        val row = store.observeBalanceRows().first().single()

        assertEquals(bca.id, row.accountId)
        assertEquals(10_000L, row.amountMinor)
        assertEquals(TransactionType.EXPENSE, row.type)
    }
}
