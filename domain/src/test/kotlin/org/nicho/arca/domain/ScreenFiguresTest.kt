package org.nicho.arca.domain

import app.cash.turbine.test
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nicho.arca.domain.calculation.BalanceCalculator
import org.nicho.arca.domain.calculation.EnvelopeCalculator
import org.nicho.arca.domain.calculation.ReportAggregator
import org.nicho.arca.domain.model.AccountFlow
import org.nicho.arca.domain.model.AccountType
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.readmodel.AccountDetailSelection
import org.nicho.arca.domain.readmodel.BalanceScale
import org.nicho.arca.domain.readmodel.ObserveAccountDetail
import org.nicho.arca.domain.readmodel.ObserveTransactionList
import org.nicho.arca.domain.readmodel.PeriodChoice
import org.nicho.arca.domain.readmodel.SortOrder
import org.nicho.arca.domain.readmodel.TransactionFilter
import org.nicho.arca.domain.readmodel.TypeFilter
import org.nicho.arca.testing.InMemoryLedger
import org.nicho.arca.testing.TestPeriodClock
import org.nicho.arca.testing.testAccount
import org.nicho.arca.testing.testCategory
import org.nicho.arca.testing.testExpense
import org.nicho.arca.testing.testIncome
import org.nicho.arca.testing.testTransfer

// The figures the prototype's screens show beyond the SDD's list: day totals, account flow,
// month-over-month change, composition, bar fill.
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenFiguresTest {

    private val bca = testAccount("BCA")
    private val gopay = testAccount("GoPay", type = AccountType.EWALLET)
    private val food = testCategory("Makanan")
    private val fees = testCategory("Biaya", isSystemLocked = true)
    private val salary = testCategory("Gaji", kind = CategoryKind.INCOME)

    // The prototype's "Yesterday −542,500": the transfer principal doesn't count, its fee does.
    @Test
    fun netByDay_excludesTransferPrincipals() {
        val yesterday = at(2026, 10, 13)
        val txs = testTransfer(bca, gopay, 200_000, yesterday, fee = 2_500L to fees) +
            testExpense(bca, food, 420_000, yesterday) +
            testExpense(bca, food, 95_000, yesterday) +
            testExpense(bca, food, 25_000, yesterday) +
            testIncome(bca, salary, 8_000_000, at(2026, 10, 1))

        val byDay = ReportAggregator.netByDay(txs, JAKARTA)

        assertEquals(-542_500L, byDay.getValue(LocalDate.of(2026, 10, 13)))
        assertEquals(8_000_000L, byDay.getValue(LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun accountFlow_countsTransfersFromTheAccountsPointOfView() {
        val txs = testTransfer(bca, gopay, 200_000, NOW, fee = 2_500L to fees) +
            testIncome(bca, salary, 8_000_000, NOW) +
            testExpense(bca, food, 50_000, NOW)

        assertEquals(AccountFlow(8_000_000, 252_500, 7_747_500), ReportAggregator.accountFlow(txs, bca.id, monthPeriod(2026, 10)))
        assertEquals(AccountFlow(200_000, 0, 200_000), ReportAggregator.accountFlow(txs, gopay.id, monthPeriod(2026, 10)))
    }

    @Test
    fun changePercent() {
        assertEquals(-13, ReportAggregator.changePercent(6_150_000, 5_350_000))
        assertEquals(50, ReportAggregator.changePercent(100, 150))
        assertNull(ReportAggregator.changePercent(0, 150))
    }

    @Test
    fun composition_ofTheMoneyHeld() {
        val shares = BalanceCalculator.composition(
            mapOf(AccountType.BANK to 750L, AccountType.EWALLET to 250L, AccountType.CASH to -100L),
        )
        assertEquals(mapOf(AccountType.BANK to 0.75f, AccountType.EWALLET to 0.25f), shares)
        assertEquals(emptyMap<AccountType, Float>(), BalanceCalculator.composition(mapOf(AccountType.CASH to 0L)))
    }

    @Test
    fun leftFraction_drainsAndIsEmptyWhenOver() {
        assertEquals(1f, EnvelopeCalculator.leftFraction(1_000, 1_000), 0f)
        assertEquals(0.1f, EnvelopeCalculator.leftFraction(2_500_000, 250_000), 0.0001f)
        assertEquals(0f, EnvelopeCalculator.leftFraction(570_000, -40_000), 0f)
    }

    @Test
    fun transactionList_filtersGroupsAndSearches() = runTest {
        val ledger = InMemoryLedger()
        val clock = TestPeriodClock(NOW, JAKARTA)
        ledger.categories.insert(food)
        ledger.categories.insert(salary)
        ledger.accounts.insert(bca)
        ledger.accounts.insert(gopay)
        ledger.transactions.insert(testExpense(bca, food, 32_000, at(2026, 10, 14)).copy(note = "Lunch at the warung"))
        ledger.transactions.insert(testExpense(gopay, food, 15_000, at(2026, 10, 14)))
        ledger.transactions.insert(testIncome(bca, salary, 8_000_000, at(2026, 10, 1)))
        val filter = MutableStateFlow(TransactionFilter(PeriodChoice.Month(ym(2026, 10))))
        val observe = ObserveTransactionList(ledger.transactions, ledger.accounts, ledger.categories, clock.periodClock,
            UnconfinedTestDispatcher(testScheduler))

        observe(filter).test {
            val all = awaitItem()
            assertEquals(47_000L, all.expenseMinor)
            assertEquals(2, all.expenseCount)
            assertEquals(listOf(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 1)), all.days.map { it.date })
            assertEquals(-47_000L, all.days[0].netMinor)

            filter.value = filter.value.copy(type = TypeFilter.EXPENSE, accountIds = setOf(bca.id))
            assertEquals(1, expectMostRecentItem().count)

            filter.value = TransactionFilter(PeriodChoice.LastDays(30), query = "WARUNG")
            assertEquals("Lunch at the warung", expectMostRecentItem().days.single().items.single().transaction.note)

            filter.value = TransactionFilter(PeriodChoice.Month(ym(2026, 10)), sort = SortOrder.LARGEST)
            val largest = expectMostRecentItem().days.single()
            assertNull(largest.date)
            assertEquals(8_000_000L, largest.items.first().transaction.amountMinor)
        }
    }

    @Test
    fun accountDetail_periodDrivesFlowTrendAndHistory() = runTest {
        val ledger = InMemoryLedger()
        val clock = TestPeriodClock(NOW, JAKARTA)
        ledger.categories.insert(food)
        ledger.categories.insert(salary)
        ledger.accounts.insert(bca.copy(openingBalanceMinor = 1_000_000))
        ledger.accounts.insert(gopay)
        ledger.transactions.insert(testIncome(bca, salary, 500_000, at(2026, 10, 2)))
        ledger.transactions.insertAtomic(testTransfer(bca, gopay, 100_000, at(2026, 10, 3)))
        ledger.transactions.insert(testExpense(bca, food, 20_000, at(2026, 9, 30)))
        val selection = MutableStateFlow(AccountDetailSelection(PeriodChoice.AllTime))

        ObserveAccountDetail(ledger.transactions, ledger.accounts, ledger.categories, clock.periodClock,
            UnconfinedTestDispatcher(testScheduler))(bca.id, selection).test {
            val all = expectMostRecentItem()!!
            assertEquals(1_380_000L, all.balanceMinor)
            assertEquals(3, all.transactionCount)
            assertEquals(LocalDate.of(2026, 9, 30), all.firstDay) // all time starts at the first entry
            assertEquals(AccountFlow(500_000, 120_000, 380_000), all.periodFlow)
            assertEquals(listOf(ym(2026, 10), ym(2026, 9)), all.history.map { it.month })
            assertEquals(BalanceScale.DAILY, all.scale) // 15 days
            assertEquals(15, all.trend.size)

            // A month: income counts in In, and the history and bars keep to it.
            selection.value = AccountDetailSelection(PeriodChoice.Month(ym(2026, 10)))
            val october = expectMostRecentItem()!!
            assertEquals(AccountFlow(500_000, 100_000, 400_000), october.periodFlow)
            assertEquals(listOf(ym(2026, 10)), october.history.map { it.month })
            assertEquals(14, october.trend.size) // Oct 1–14: never past today
            assertEquals(listOf(BalanceScale.DAILY, BalanceScale.WEEKLY), october.scales)
            assertEquals(1_480_000L, october.trend[1].balanceMinor) // end of Oct 2
            assertEquals(AccountFlow(500_000, 0, 500_000), october.trend[1].flow)
            assertEquals(51, october.trend[1].changePercent) // +500,000 on the 980,000 the day before

            // A selected bar narrows In / Out / Net and the history to its days.
            selection.value = october.selection.copy(focus = LocalDate.of(2026, 10, 3))
            val third = expectMostRecentItem()!!
            assertEquals(AccountFlow(0, 100_000, -100_000), third.shownFlow)
            assertEquals(1, third.shownCount)

            // Weeks are Monday-first and clipped to the period: Oct 1–4, 5–11, 12–14.
            selection.value = AccountDetailSelection(PeriodChoice.Month(ym(2026, 10)), scale = BalanceScale.WEEKLY)
            val weekly = expectMostRecentItem()!!
            assertEquals(listOf(4L, 7L, 3L), weekly.trend.map { it.last.toEpochDay() - it.first.toEpochDay() + 1 })

            selection.value = AccountDetailSelection(PeriodChoice.Month(ym(2026, 10)), type = TypeFilter.TRANSFER)
            assertEquals(1, expectMostRecentItem()!!.shownCount)
        }
    }
}
