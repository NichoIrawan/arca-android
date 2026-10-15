package org.nicho.arca.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nicho.arca.domain.adjustment.BalanceAdjustmentService
import org.nicho.arca.domain.calculation.BalanceCalculator
import org.nicho.arca.domain.calculation.ReportAggregator
import org.nicho.arca.domain.model.BalanceRow
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.testing.InMemoryLedger
import org.nicho.arca.testing.testAccount
import org.nicho.arca.testing.testCategory
import org.nicho.arca.testing.testExpense

// "Adjust balance" (client request, 2026-10-14) and the negative-balance warning behind it.
class BalanceAdjustmentTest {

    private val ledger = InMemoryLedger()
    private val bca = testAccount("BCA", openingBalanceMinor = 1_000_000)
    private val food = testCategory()

    private suspend fun seed(): BalanceAdjustmentService {
        ledger.accounts.insert(bca)
        ledger.categories.insert(food)
        return BalanceAdjustmentService(ledger.transactions, ledger.transactions, ledger.accounts)
    }

    @Test
    fun adjustingDown_recordsTheDifferenceAsOneOutgoingRow() = runTest {
        val service = seed()
        ledger.transactions.insert(testExpense(bca, food, 150_000, at(2026, 10, 1)))

        val row = service.adjust(bca.id, targetMinor = 800_000, at = at(2026, 10, 14), note = "  Counted at the ATM ")!!

        assertEquals(TransactionType.ADJUSTMENT_OUT, row.type)
        assertEquals(50_000L, row.amountMinor) // 850,000 → 800,000
        assertEquals("Counted at the ATM", row.note)
        assertNull(row.categoryId)
        assertEquals(800_000L, service.currentBalance(bca.id))
    }

    @Test
    fun adjustingUp_andToANegativeBalance_areBothAllowed() = runTest {
        val service = seed()
        assertEquals(TransactionType.ADJUSTMENT_IN, service.adjust(bca.id, 1_250_000, at(2026, 10, 14), null)!!.type)
        assertEquals(TransactionType.ADJUSTMENT_OUT, service.adjust(bca.id, -20_000, at(2026, 10, 14), null)!!.type)
        assertEquals(-20_000L, service.currentBalance(bca.id))
    }

    @Test
    fun aMatchingBalance_writesNothing() = runTest {
        val service = seed()
        assertNull(service.adjust(bca.id, 1_000_000, at(2026, 10, 14), null))
        assertEquals(0, ledger.transactions.count())
    }

    @Test
    fun anAdjustmentIsNeitherIncomeNorSpending() = runTest {
        val service = seed()
        service.adjust(bca.id, 2_000_000, at(2026, 10, 14), null)
        val txs = ledger.transactions.observeInPeriod(monthPeriod(2026, 10)).first()
        val summary = ReportAggregator.summary(txs, monthPeriod(2026, 10))
        assertEquals(0L, summary.incomeMinor)
        assertEquals(0L, summary.expenseMinor)
        // …but the account's own In / Out sees it.
        assertEquals(1_000_000L, ReportAggregator.accountFlow(txs, bca.id, monthPeriod(2026, 10)).inMinor)
    }

    @Test
    fun projected_removesTheEditedOriginalBeforeAddingTheNewRow() {
        val original = BalanceRow(bca.id, 30_000, TransactionType.EXPENSE)
        val edited = BalanceRow(bca.id, 50_000, TransactionType.EXPENSE)
        assertEquals(80_000L, BalanceCalculator.projected(100_000, listOf(edited), listOf(original)))
        assertTrue(BalanceCalculator.isNegative(BalanceCalculator.projected(10_000, listOf(edited))))
    }

    @Test
    fun balancesAt_foldsEachEndOnce() {
        val txs = listOf(
            testExpense(bca, food, 100, at(2026, 10, 2)),
            testExpense(bca, food, 200, at(2026, 10, 5)),
        )
        val ends = listOf(at(2026, 10, 1), at(2026, 10, 3), at(2026, 10, 6))
        assertEquals(listOf(1_000L, 900L, 700L), BalanceCalculator.balancesAt(1_000, txs, ends))
    }
}

class BudgetStepTest {
    @Test
    fun steps_byFiftyThousand_withinBounds() {
        assertEquals(550_000L, org.nicho.arca.domain.budget.BudgetStep.up(500_000))
        assertEquals(450_000L, org.nicho.arca.domain.budget.BudgetStep.down(500_000))
        assertEquals(50_000L, org.nicho.arca.domain.budget.BudgetStep.down(50_000))
        assertEquals(100_000_000L, org.nicho.arca.domain.budget.BudgetStep.up(99_990_000))
        assertEquals(50_000L, org.nicho.arca.domain.budget.BudgetStep.up(null))
    }
}
