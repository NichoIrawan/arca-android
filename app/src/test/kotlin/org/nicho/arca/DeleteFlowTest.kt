package org.nicho.arca

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipe
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.nicho.arca.designsystem.ArcaTheme
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionListItem
import org.nicho.arca.domain.readmodel.TransactionDay
import org.nicho.arca.domain.repository.ThemePreference
import org.nicho.arca.domain.transfer.TransferService
import org.nicho.arca.feature.transactions.TransactionDayList
import org.nicho.arca.feature.transactions.TransactionDetailUiState
import org.nicho.arca.feature.transactions.TransactionDetailViewModel
import org.nicho.arca.testing.InMemoryLedger
import org.nicho.arca.testing.testAccount
import org.nicho.arca.testing.testCategory
import org.nicho.arca.testing.testExpense
import org.nicho.arca.undo.UndoCoordinator
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.milliseconds

/**
 * Deleting a transaction (FR-106, client request 2026-10-15): a swipe that stops short changes
 * nothing; one past the line asks first and deletes only on Delete; deleting from the detail
 * screen closes it and removes the row once the undo window ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp-port-hdpi")
class DeleteFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val bca = testAccount("BCA")
    private val food = testCategory("Makanan")
    private val tx = testExpense(bca, food, 45_000, Instant.parse("2026-10-14T05:00:00Z"))
    private val item = TransactionListItem(
        transaction = tx,
        accountName = bca.name,
        accountColorHex = bca.colorHex,
        counterAccountName = null,
        categoryName = food.name,
        categoryIcon = food.icon,
        categoryColorHex = food.colorHex,
    )

    private fun showList(onDelete: (Transaction) -> Unit) {
        compose.setContent {
            ArcaTheme(ThemePreference.LIGHT) {
                TransactionDayList(listOf(TransactionDay(LocalDate.of(2026, 10, 14), -45_000, listOf(item))), {}, onDelete)
            }
        }
    }

    // The list's row; once the sheet is open it shows a second copy of the row.
    private fun rowLeft() = compose.onAllNodesWithText(food.name, useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot.left

    /**
     * Drags the row from its right edge to [toFraction] of the screen's width, lets go, and
     * checks the row slides all the way back to rest, whichever side of the line it was let go.
     */
    private fun swipeRow(toFraction: Float) {
        val restLeft = rowLeft()
        val y = compose.onNodeWithText(food.name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center.y
        compose.onRoot().performTouchInput {
            swipe(Offset(right - 10f, y), Offset(width * toFraction, y), durationMillis = 300)
        }
        compose.waitForIdle()
        assertEquals(restLeft, rowLeft(), 0.5f)
    }

    @Test
    fun aShortSwipe_springsBack_andDeletesNothing() {
        val deleted = mutableListOf<Transaction>()
        showList { deleted += it }

        swipeRow(toFraction = 0.8f)
        compose.waitForIdle()

        compose.onNodeWithText("Delete this transaction?").assertDoesNotExist()
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun aFullSwipe_asksFirst_thenDeletesOnDelete() {
        val deleted = mutableListOf<Transaction>()
        showList { deleted += it }

        swipeRow(toFraction = 0.05f)
        compose.waitForIdle()

        compose.onNodeWithText("Delete this transaction?").assertIsDisplayed()
        assertTrue(deleted.isEmpty()) // nothing until it's confirmed

        compose.onNode(hasText("Delete") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(listOf(tx), deleted)
    }

    @Test
    fun deletingFromTheDetail_closesIt_andRemovesTheRowAfterTheWindow() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val ledger = InMemoryLedger()
            ledger.categories.insert(food)
            ledger.accounts.insert(bca)
            ledger.transactions.insert(tx)
            val undo = UndoCoordinator(backgroundScope, ledger.transactions, TransferService(ledger.transactions, ledger.categories))
            val vm = TransactionDetailViewModel(
                SavedStateHandle(mapOf("transactionId" to tx.id.value.toString())),
                ledger.transactions,
                ledger.accounts,
                ledger.categories,
                undo,
            )
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            assertTrue(vm.state.value is TransactionDetailUiState.Ready)

            vm.onDelete()
            runCurrent()
            assertEquals(TransactionDetailUiState.Gone, vm.state.value) // the screen closes at once
            assertEquals(1, ledger.transactions.count())                 // the row waits out the undo

            advanceTimeBy((UndoCoordinator.WINDOW_MILLIS + 1).milliseconds)
            runCurrent()
            assertEquals(0, ledger.transactions.count())
        } finally {
            Dispatchers.resetMain()
        }
    }
}
