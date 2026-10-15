package org.nicho.arca

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nicho.arca.domain.transfer.TransferService
import org.nicho.arca.testing.InMemoryLedger
import org.nicho.arca.testing.testAccount
import org.nicho.arca.testing.testCategory
import org.nicho.arca.testing.testExpense
import org.nicho.arca.testing.testTransfer
import org.nicho.arca.undo.UndoCoordinator
import kotlin.time.Duration.Companion.milliseconds

// SDD-ARC-002 §9.7, DD-A11 · FR-106: rows vanish at once; the store changes only after 5 seconds.
@OptIn(ExperimentalCoroutinesApi::class)
class UndoCoordinatorTest {

    private val ledger = InMemoryLedger()
    private val bca = testAccount("BCA")
    private val gopay = testAccount("GoPay")
    private val food = testCategory()

    private suspend fun TestScope.setUp(): UndoCoordinator {
        ledger.categories.seedDefaultsIfNeeded()
        ledger.categories.insert(food)
        ledger.accounts.insert(bca)
        ledger.accounts.insert(gopay)
        return UndoCoordinator(backgroundScope, ledger.transactions, TransferService(ledger.transactions, ledger.categories))
    }

    @Test
    fun aDeletion_commitsOnlyAfterTheWindow() = runTest {
        val undo = setUp()
        val tx = testExpense(bca, food, 10_000, java.time.Instant.parse("2026-10-14T05:00:00Z"))
        ledger.transactions.insert(tx)

        undo.deleteTransaction(tx.id)
        advanceTimeBy((UndoCoordinator.WINDOW_MILLIS - 1).milliseconds)
        assertEquals(1, ledger.transactions.count()) // still there: only hidden

        advanceTimeBy(2.milliseconds)
        runCurrent()
        assertEquals(0, ledger.transactions.count())
        assertNull(undo.pending.value)
    }

    @Test
    fun undo_withinTheWindow_changesNothing() = runTest {
        val undo = setUp()
        val tx = testExpense(bca, food, 10_000, java.time.Instant.parse("2026-10-14T05:00:00Z"))
        ledger.transactions.insert(tx)

        undo.deleteTransaction(tx.id)
        undo.undo()
        advanceTimeBy((UndoCoordinator.WINDOW_MILLIS * 2).milliseconds)
        runCurrent()

        assertEquals(1, ledger.transactions.count())
    }

    @Test
    fun deletingATransfer_hidesAndRemovesTheWholeGroup() = runTest {
        val undo = setUp()
        val fees = ledger.categories.feesCategory()
        val members = testTransfer(bca, gopay, 200_000, java.time.Instant.parse("2026-10-14T05:00:00Z"), fee = 2_500L to fees)
        ledger.transactions.insertAtomic(members)

        undo.deleteTransfer(members.first().transferGroupId!!)
        runCurrent()
        assertEquals(3, undo.pending.value?.memberCount)
        assertEquals(true, members.all { undo.pending.value?.hides(it) == true })

        advanceTimeBy((UndoCoordinator.WINDOW_MILLIS + 1).milliseconds)
        runCurrent()
        assertEquals(0, ledger.transactions.count())
    }
}
