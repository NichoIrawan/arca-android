package org.nicho.arca.domain

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.AccountType
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.readmodel.ObserveAccounts
import org.nicho.arca.domain.readmodel.ObserveBudget
import org.nicho.arca.domain.readmodel.ObserveCategoryDetail
import org.nicho.arca.domain.readmodel.ObserveReports
import org.nicho.arca.domain.readmodel.ObserveSummary
import org.nicho.arca.domain.readmodel.ReportRange
import org.nicho.arca.domain.readmodel.ReportSelection
import org.nicho.arca.domain.readmodel.TrendGranularity
import org.nicho.arca.domain.readmodel.SortOrder
import org.nicho.arca.testing.InMemoryLedger
import org.nicho.arca.testing.TestPeriodClock
import org.nicho.arca.testing.testAccount
import org.nicho.arca.testing.testCategory
import org.nicho.arca.testing.testExpense
import org.nicho.arca.testing.testIncome
import org.nicho.arca.testing.testTransfer

// SDD-ARC-002 §14.2: write → the derived value re-emits. Turbine over in-memory stores.
//
// One write can make several upstream flows emit in turn, so a combine briefly sees half-updated
// inputs (Room does the same). Tests read the settled value with expectMostRecentItem().
@OptIn(ExperimentalCoroutinesApi::class)
class ReadModelsTest {

    private val ledger = InMemoryLedger()
    private val clock = TestPeriodClock(NOW, JAKARTA)
    private val bca = testAccount("BCA", openingBalanceMinor = 1_000_000, sortOrder = 0)
    private val gopay = testAccount("GoPay", type = AccountType.EWALLET, openingBalanceMinor = 100_000, sortOrder = 1)
    private val cash = testAccount("Dompet", type = AccountType.CASH, openingBalanceMinor = 50_000, sortOrder = 2)
    private val food = testCategory("Makanan")
    private val salary = testCategory("Gaji", kind = CategoryKind.INCOME)

    private suspend fun seed() {
        ledger.categories.seedDefaultsIfNeeded()
        listOf(bca, gopay, cash).forEach { ledger.accounts.insert(it) }
        ledger.categories.insert(food)
        ledger.budgets.setRollsOver(food.id, true) // roll-over goes through the budget store, like in the app
        ledger.categories.insert(salary)
    }

    // "Reactive derived refresh": the OPEN-D03 scenario as a test.
    @Test
    fun summary_netPositionMovesByExactlyTheNewTransaction() = runTest {
        seed()
        val observe = ObserveSummary(ledger.transactions, ledger.accounts, ledger.categories, ledger.budgets, clock.periodClock,
            dispatcher = UnconfinedTestDispatcher(testScheduler))

        observe().test {
            val before = awaitItem()
            assertEquals(1_150_000L, before.netPositionMinor)
            assertTrue(before.isEmpty)

            ledger.transactions.insert(testExpense(bca, food, 75_000, NOW))

            val after = expectMostRecentItem()
            assertEquals(1_075_000L, after.netPositionMinor)
            assertEquals(75_000L, after.summary.expenseMinor)
            assertEquals(listOf(food.id), after.categorySpend.map { it.categoryId })
            assertEquals(1, after.recent.size)
            assertEquals(false, after.isEmpty)
        }
    }

    // "Backdate into a closed month": last month changes, this month's summary doesn't.
    @Test
    fun summary_aBackdatedExpense_changesTheBalanceButNotThisMonth() = runTest {
        seed()
        val observe = ObserveSummary(ledger.transactions, ledger.accounts, ledger.categories, ledger.budgets, clock.periodClock,
            dispatcher = UnconfinedTestDispatcher(testScheduler))

        observe().test {
            awaitItem()
            ledger.transactions.insert(testExpense(bca, food, 40_000, at(2026, 9, 20)))

            val after = expectMostRecentItem()
            assertEquals(1_110_000L, after.netPositionMinor)
            assertEquals(0L, after.summary.expenseMinor)
        }
    }

    @Test
    fun accounts_groupedInFr204Order_withSubtotals_archivedApart() = runTest {
        seed()
        val old = testAccount("Lama", openingBalanceMinor = 5_000, isArchived = true)
        ledger.accounts.insert(old)
        ledger.transactions.insertAtomic(testTransfer(bca, gopay, 200_000, NOW))

        ObserveAccounts(ledger.transactions, ledger.accounts, UnconfinedTestDispatcher(testScheduler))().test {
            val model = awaitItem()
            assertEquals(listOf(AccountType.BANK, AccountType.EWALLET, AccountType.CASH), model.groups.map { it.type }) // largest first
            assertEquals(1f, model.groups.map { it.share }.sum(), 0.0001f)
            assertEquals(800_000L, model.groups.single { it.type == AccountType.BANK }.subtotalMinor)
            assertEquals(300_000L, model.groups.single { it.type == AccountType.EWALLET }.subtotalMinor)
            assertEquals(listOf(old.id), model.archived.map { it.account.id })
            assertEquals(1_150_000L, model.netPositionMinor)
        }
    }

    @Test
    fun reports_followTheSelectedMonth() = runTest {
        seed()
        ledger.transactions.insert(testExpense(bca, food, 30_000, at(2026, 9, 10)))
        ledger.transactions.insert(testExpense(bca, food, 70_000, at(2026, 10, 2)))
        val selection = MutableStateFlow(ReportSelection(ReportRange.Month(ym(2026, 10)), TrendGranularity.DAILY))

        ObserveReports(ledger.transactions, ledger.categories, clock.periodClock, UnconfinedTestDispatcher(testScheduler))(selection).test {
            val october = expectMostRecentItem()
            assertEquals(70_000L, october.expenses.totalMinor)
            assertEquals(31, october.trend.size)
            assertEquals(30_000L, october.expenses.previousMinor)
            assertEquals(133, october.expenses.changePercent)
            assertEquals(ym(2026, 9), october.earliestMonth)

            selection.value = ReportSelection(ReportRange.Month(ym(2026, 9)), TrendGranularity.WEEKLY)
            val september = expectMostRecentItem()
            assertEquals(ym(2026, 9), september.month)
            assertEquals(30_000L, september.expenses.totalMinor)
            // 1–7, 8–14, 15–21, 22–30: the month's tail joins the fourth week
            assertEquals(listOf(7, 7, 7, 9), september.trend.map { it.dayCount })
            assertEquals(listOf(0L, 30_000L, 0L, 0L), september.trend.map { it.expenseMinor })
            assertEquals(1f, september.trend[1].expenseFraction)
        }
    }

    @Test
    fun reports_rollingRanges_compareWithTheEqualRangeBefore() = runTest {
        seed()
        // NOW is Oct 14: the last 7 days are Oct 8–14, the 7 before them Oct 1–7.
        ledger.transactions.insert(testExpense(bca, food, 40_000, at(2026, 10, 12)))
        ledger.transactions.insert(testExpense(bca, food, 50_000, at(2026, 10, 3)))
        ledger.transactions.insert(testExpense(bca, food, 999_000, at(2026, 9, 1)))

        val selection = MutableStateFlow(ReportSelection(ReportRange.LastDays(7)))
        ObserveReports(ledger.transactions, ledger.categories, clock.periodClock, UnconfinedTestDispatcher(testScheduler))(selection).test {
            val week = expectMostRecentItem()
            assertEquals(TrendGranularity.DAILY, week.granularity) // 7 days is always daily
            assertEquals(7, week.trend.size)
            assertEquals(java.time.LocalDate.of(2026, 10, 8), week.firstDay)
            assertEquals(40_000L, week.expenses.totalMinor)
            assertEquals(50_000L, week.expenses.previousMinor)
            assertEquals(-20, week.expenses.changePercent)

            selection.value = ReportSelection(ReportRange.LastDays(28), TrendGranularity.WEEKLY)
            val month = expectMostRecentItem()
            assertEquals(4, month.trend.size)
            assertEquals(90_000L, month.expenses.totalMinor)
        }
    }

    @Test
    fun reports_foldEverythingAfterTheFourLargestIntoOne() = runTest {
        seed()
        val extra = (1..5).map { testCategory("C$it") }
        extra.forEach { ledger.categories.insert(it) }
        extra.forEachIndexed { i, c -> ledger.transactions.insert(testExpense(bca, c, (i + 1) * 10_000L, at(2026, 10, 5))) }

        ObserveReports(ledger.transactions, ledger.categories, clock.periodClock, UnconfinedTestDispatcher(testScheduler))(
            MutableStateFlow(ReportSelection(ReportRange.Month(ym(2026, 10)))),
        ).test {
            val model = expectMostRecentItem()
            assertEquals(listOf("C5", "C4", "C3", "C2", null), model.expenseCategories.map { it.name })
            assertEquals(10_000L, model.expenseCategories.last().totalMinor)
            assertEquals(listOf(extra[0].id), model.expenseCategories.last().restIds)
        }
    }

    @Test
    fun reports_measureIncomeAndTheNetToo() = runTest {
        seed()
        ledger.transactions.insert(testIncome(bca, salary, 500_000, at(2026, 10, 1)))
        ledger.transactions.insert(testExpense(bca, food, 200_000, at(2026, 10, 9)))
        ledger.transactions.insert(testIncome(bca, salary, 100_000, at(2026, 9, 1)))
        ledger.transactions.insertAtomic(testTransfer(bca, gopay, 50_000, at(2026, 10, 2))) // never counts

        ObserveReports(ledger.transactions, ledger.categories, clock.periodClock, UnconfinedTestDispatcher(testScheduler))(
            MutableStateFlow(ReportSelection(ReportRange.Month(ym(2026, 10)), TrendGranularity.WEEKLY)),
        ).test {
            val model = expectMostRecentItem()
            assertEquals(500_000L, model.income.totalMinor)
            assertEquals(400, model.income.changePercent)
            assertEquals(300_000L, model.net.totalMinor)
            assertEquals(200_000L, model.net.changeMinor) // September's net was 100,000
            assertNull(model.net.changePercent)
            assertEquals(listOf(500_000L, -200_000L, 0L, 0L), model.trend.map { it.netMinor })
            assertEquals(listOf(1f, -0.4f, 0f, 0f), model.trend.map { it.netFraction })
            assertEquals(listOf(1f, 0f, 0f, 0f), model.trend.map { it.incomeFraction })
            assertEquals(listOf("Gaji"), model.incomeCategories.map { it.name })
            assertEquals(1f, model.incomeShare)
            assertEquals(0.4f, model.expenseShare)
        }
    }

    // "Envelopes react to a backdate": derived carry needs no repair (DD-A19).
    @Test
    fun budget_aBackdatedExpense_changesThisMonthsCarry() = runTest {
        seed()
        ledger.budgets.setBudget(food.id, ym(2026, 9), 1_000_000)
        ledger.transactions.insert(testExpense(bca, food, 600_000, at(2026, 9, 15)))
        ledger.transactions.insert(testIncome(bca, salary, 15_000_000, at(2026, 10, 1)))

        ObserveBudget(ledger.transactions, ledger.categories, ledger.budgets, clock.periodClock,
            UnconfinedTestDispatcher(testScheduler))(MutableStateFlow(ym(2026, 10))).test {
            val before = awaitItem()
            val envelope = before.summary.envelopes.single()
            assertEquals(400_000L, envelope.carryMinor)
            assertEquals(1_400_000L, envelope.availableMinor)
            assertEquals(listOf(salary.id to 15_000_000L), before.income.map { it.categoryId to it.totalMinor })
            assertEquals(food.name, before.categories.getValue(envelope.categoryId).name)

            ledger.transactions.insert(testExpense(gopay, food, 100_000, at(2026, 9, 28)))

            assertEquals(300_000L, expectMostRecentItem().summary.envelopes.single().carryMinor)
        }
    }

    // FR-268: every transaction, no truncation; the account filter narrows the list only.
    @Test
    fun categoryDetail_listsAll18_andFiltersByAccountWithoutTouchingTheEnvelope() = runTest {
        seed()
        ledger.budgets.setBudget(food.id, ym(2026, 10), 2_000_000)
        val payers = listOf(bca, gopay, cash)
        repeat(18) { i -> ledger.transactions.insert(testExpense(payers[i % 3], food, 10_000, at(2026, 10, 1 + i % 13))) }
        val filter = MutableStateFlow<AccountId?>(null)

        ObserveCategoryDetail(ledger.transactions, ledger.accounts, ledger.categories, ledger.budgets, clock.periodClock,
            UnconfinedTestDispatcher(testScheduler))(food.id, MutableStateFlow(ym(2026, 10)), filter, MutableStateFlow(SortOrder.NEWEST)).test {
            val all = awaitItem()!!
            assertEquals(18, all.count)
            assertEquals(18, all.days.sumOf { it.items.size })
            assertEquals(payers.map { it.id }.toSet(), all.accountIds.toSet())
            assertEquals(180_000L, all.envelope!!.spentMinor)
            assertNull(all.notBudgeted)

            filter.value = gopay.id
            val gopayOnly = expectMostRecentItem()!!
            assertEquals(6, gopayOnly.count)
            assertTrue(gopayOnly.days.flatMap { it.items }.all { it.transaction.accountId == gopay.id })
            assertEquals(180_000L, gopayOnly.envelope!!.spentMinor)
        }
    }
}
