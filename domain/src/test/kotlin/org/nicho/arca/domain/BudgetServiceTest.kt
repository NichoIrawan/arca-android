package org.nicho.arca.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nicho.arca.domain.budget.BudgetIssue
import org.nicho.arca.domain.budget.BudgetService
import org.nicho.arca.domain.error.DomainError
import org.nicho.arca.domain.model.BudgetAllocation
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.testing.InMemoryLedger
import org.nicho.arca.testing.TestPeriodClock
import org.nicho.arca.testing.testCategory

// SDD-ARC-001 §12.2 BudgetValidatorTests · FR-260, FR-261, FR-265.
class BudgetServiceTest {

    private val ledger = InMemoryLedger()
    private val clock = TestPeriodClock(NOW, JAKARTA)
    private val service = BudgetService(ledger.categories, ledger.budgets, clock.periodClock)
    private val transport = testCategory("Transportasi")

    @Test
    fun setBudget_appliesFromTheCurrentMonth() = runTest {
        ledger.categories.insert(transport)

        service.setBudget(transport.id, 1_300_000)

        assertEquals(listOf(BudgetAllocation(transport.id, ym(2026, 10), 1_300_000)), ledger.budgets.observeAllocations().first())
    }

    // The invariant: after every write, the category's budget equals its latest allocation.
    @Test
    fun aSequenceOfChanges_keepsCategoryAndHistoryInStep() = runTest {
        ledger.categories.insert(transport)

        for ((month, amount) in listOf(ym(2026, 10) to 1_000_000L, ym(2026, 10) to 1_200_000L, ym(2026, 11) to null, ym(2026, 12) to 900_000L)) {
            clock.now = at(month.year, month.monthValue, 2)
            service.setBudget(transport.id, amount)

            val latest = ledger.budgets.observeAllocations().first().maxBy { it.month }
            assertEquals(latest.amountMinor, ledger.categories.byId(transport.id)!!.budgetMinor)
        }
        assertEquals(3, ledger.budgets.observeAllocations().first().size) // October edited in place
    }

    @Test
    fun aZeroAmount_isRejected_andNothingIsWritten() = runTest {
        ledger.categories.insert(transport)

        val error = assertThrowsSuspend<DomainError.BudgetInvalid> { service.setBudget(transport.id, 0) }

        assertEquals(listOf(BudgetIssue.AMOUNT_NOT_POSITIVE), error.issues)
        assertTrue(ledger.budgets.observeAllocations().first().isEmpty())
    }

    @Test
    fun fees_isNeverBudgeted() = runTest {
        ledger.categories.seedDefaultsIfNeeded()
        val fees = ledger.categories.feesCategory()

        val error = assertThrowsSuspend<DomainError.BudgetInvalid> { service.setBudget(fees.id, 50_000) }
        assertEquals(listOf(BudgetIssue.SYSTEM_LOCKED_CATEGORY), error.issues)
        assertThrowsSuspend<DomainError.BudgetInvalid> { service.setRollsOver(fees.id, true) }
    }

    @Test
    fun anIncomeCategory_isNeverBudgeted() = runTest {
        val salary = testCategory("Gaji", kind = CategoryKind.INCOME)
        ledger.categories.insert(salary)

        val error = assertThrowsSuspend<DomainError.BudgetInvalid> { service.setBudget(salary.id, 1_000_000) }

        assertEquals(listOf(BudgetIssue.INCOME_CATEGORY), error.issues)
    }

    @Test
    fun setRollsOver_writesTheFlag() = runTest {
        ledger.categories.insert(transport)

        service.setRollsOver(transport.id, true)

        assertTrue(ledger.categories.byId(transport.id)!!.rollsOver)
    }
}
