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
import org.nicho.arca.data.db.dao.TransactionDao
import org.nicho.arca.data.db.entities.BalanceRowEntity

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {

    private lateinit var db: ArcaDatabase
    private lateinit var dao: TransactionDao

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        dao = db.transactionDao()
        db.accountDao().insertAll(listOf(account("bca"), account("gopay")))
        db.categoryDao().insert(category("food", name = "Makanan"))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun observeInPeriod_isHalfOpen() = runTest {
        val from = T0
        val to = T0 + 1_000
        dao.insertAll(
            listOf(
                transaction("before", "bca", date = from - 1),
                transaction("atStart", "bca", date = from),
                transaction("inside", "bca", date = from + 500),
                transaction("atEnd", "bca", date = to),
            )
        )

        val ids = dao.observeInPeriod(from, to).first().map { it.id }

        assertEquals(listOf("inside", "atStart"), ids) // newest first; start in, end out
    }

    @Test
    fun observeForAccount_returnsOnlyThatAccountsRows() = runTest {
        dao.insertAll(
            listOf(
                transaction("a", "bca", date = T0),
                transaction("b", "gopay", date = T0 + 1),
                transaction("c", "bca", date = T0 + 2),
            )
        )

        val ids = dao.observeForAccount("bca").first().map { it.id }

        assertEquals(listOf("c", "a"), ids)
    }

    @Test
    fun observeBalanceRows_isTheThreeColumnProjection() = runTest {
        dao.insertAll(
            listOf(
                transaction("a", "bca", amountMinor = 10_000, type = "expense"),
                transaction("b", "gopay", amountMinor = 3_000_000_000, type = "income", categoryId = null),
            )
        )

        val rows = dao.observeBalanceRows().first().sortedBy { it.accountId }

        // 3,000,000,000 is past Int.MAX_VALUE: proves the column round-trips as Long (DD-A08).
        assertEquals(
            listOf(
                BalanceRowEntity("bca", 10_000, "expense"),
                BalanceRowEntity("gopay", 3_000_000_000, "income"),
            ),
            rows,
        )
    }

    @Test
    fun observeRecent_joinsDisplayFieldsAndLimits() = runTest {
        dao.insertAll(
            listOf(
                transaction("lunch", "bca", date = T0),
                transaction(
                    "out", "bca", type = "transferOut", date = T0 + 1, categoryId = null,
                    transferGroupId = "g1", counterAccountId = "gopay",
                ),
                transaction("old", "bca", date = T0 - 1),
            )
        )

        val rows = dao.observeRecent(limit = 2).first()

        assertEquals(listOf("out", "lunch"), rows.map { it.transaction.id })
        val transfer = rows[0]
        assertEquals("BCA", transfer.accountName)
        assertEquals("GOPAY", transfer.counterAccountName)
        assertNull(transfer.categoryName)
        val lunch = rows[1]
        assertEquals("Makanan", lunch.categoryName)
        assertNull(lunch.counterAccountName)
    }

    @Test
    fun deleteGroup_removesPrincipalsAndFee() = runTest {
        db.categoryDao().insert(category("fees", isSystemLocked = true))
        dao.insertAll(
            listOf(
                transaction("out", "bca", type = "transferOut", categoryId = null, transferGroupId = "g1", counterAccountId = "gopay"),
                transaction("in", "gopay", type = "transferIn", categoryId = null, transferGroupId = "g1", counterAccountId = "bca"),
                transaction("fee", "bca", amountMinor = 2_500, categoryId = "fees", transferGroupId = "g1"),
                transaction("unrelated", "bca"),
            )
        )

        dao.deleteGroup("g1")

        assertEquals(listOf("unrelated"), dao.getAll().map { it.id })
    }
}
