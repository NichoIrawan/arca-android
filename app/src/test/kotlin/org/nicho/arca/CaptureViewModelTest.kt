package org.nicho.arca

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.repository.CaptureContext
import org.nicho.arca.domain.repository.CaptureContextStore
import org.nicho.arca.domain.transfer.TransferService
import org.nicho.arca.domain.validation.TransactionValidator
import org.nicho.arca.feature.capture.CaptureIssue
import org.nicho.arca.feature.capture.CaptureMode
import org.nicho.arca.feature.capture.CaptureRequest
import org.nicho.arca.feature.capture.CaptureViewModel
import org.nicho.arca.testing.InMemoryLedger
import org.nicho.arca.testing.testAccount
import org.nicho.arca.testing.testCategory

// SDD-ARC-002 §14.1: ViewModels are unit-tested (parse → validate → save) against in-memory stores.
@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {

    private val ledger = InMemoryLedger()
    private val bca = testAccount("BCA", isFavorite = true)
    private val gopay = testAccount("GoPay")
    private val food = testCategory("Makanan")
    private val salary = testCategory("Gaji", kind = CategoryKind.INCOME)
    private val context = FakeCaptureContext()
    private val now = Instant.parse("2026-10-14T05:00:00Z")
    private lateinit var vm: CaptureViewModel

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        ledger.categories.seedDefaultsIfNeeded()
        ledger.categories.insert(food)
        ledger.categories.insert(salary)
        ledger.accounts.insert(bca)
        ledger.accounts.insert(gopay)
        vm = CaptureViewModel(
            accountsStore = ledger.accounts,
            categoriesStore = ledger.categories,
            reading = ledger.transactions,
            writing = ledger.transactions,
            transfers = TransferService(ledger.transactions, ledger.categories),
            captureContext = context,
            validator = TransactionValidator.standard,
            clock = Clock.fixed(now, ZoneId.of("Asia/Jakarta")),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun anExpense_parsesTheAmount_savesOnce_andRemembersTheAccount() = runTest {
        vm.start(CaptureRequest())
        vm.onAmountChange("50rb")
        vm.onCategoryChange(food.id)

        vm.onSave()

        val saved = ledger.transactions.allForExport().single()
        assertEquals(50_000L, saved.amountMinor)
        assertEquals(TransactionType.EXPENSE, saved.type)
        assertEquals(bca.id, saved.accountId) // the favourite is preselected
        assertTrue(vm.state.value.isDone)
        assertEquals(bca.id, context.state.value.lastAccountId) // FR-104
    }

    @Test
    fun everyProblemIsReportedAtOnce_andNothingIsWritten() = runTest {
        vm.start(CaptureRequest())
        vm.onAmountChange("1.5") // ambiguous without a suffix (DD-14)
        vm.onSave()
        assertEquals(setOf(CaptureIssue.AMOUNT_UNCLEAR), vm.state.value.issues)

        vm.onAmountChange("50000")
        vm.onSave() // no category yet
        assertEquals(setOf(CaptureIssue.CATEGORY_REQUIRED), vm.state.value.issues)
        assertEquals(0, ledger.transactions.count())
        assertFalse(vm.state.value.isDone)
    }

    @Test
    fun switchingToIncome_offersIncomeCategoriesOnly() = runTest {
        vm.start(CaptureRequest())
        vm.onModeChange(CaptureMode.INCOME)

        assertTrue(vm.state.value.categories.all { it.kind == CategoryKind.INCOME })
        assertTrue(vm.state.value.categories.any { it.id == salary.id })
    }

    @Test
    fun aTransferWithAFee_writesThreeLinkedRows() = runTest {
        vm.start(CaptureRequest(mode = CaptureMode.TRANSFER, accountId = bca.id))
        vm.onDestinationChange(gopay.id)
        vm.onAmountChange("200000")
        vm.onFeeChange("2500")

        vm.onSave()

        val rows = ledger.transactions.allForExport()
        assertEquals(3, rows.size)
        assertEquals(1, rows.mapNotNull { it.transferGroupId }.toSet().size)
    }

    @Test
    fun aTransferToTheSameAccount_isRejected() = runTest {
        vm.start(CaptureRequest(mode = CaptureMode.TRANSFER, accountId = bca.id))
        vm.onDestinationChange(bca.id)
        vm.onAmountChange("10000")

        vm.onSave()

        assertTrue(CaptureIssue.SAME_ACCOUNT in vm.state.value.issues)
        assertEquals(0, ledger.transactions.count())
    }

    @Test
    fun editing_updatesTheSameRow() = runTest {
        vm.start(CaptureRequest())
        vm.onAmountChange("10000")
        vm.onCategoryChange(food.id)
        vm.onSave()
        val id = ledger.transactions.allForExport().single().id

        vm.start(CaptureRequest(editing = id))
        vm.onAmountChange("12000")
        vm.onSave()

        val rows = ledger.transactions.allForExport()
        assertEquals(1, rows.size)
        assertEquals(12_000L, rows.single().amountMinor)
    }

    // Client decision, 2026-10-14: warn before an expense takes an account below zero, never block.
    @Test
    fun anExpensePastTheBalance_warnsButStillSaves() = runTest {
        vm.start(CaptureRequest(accountId = gopay.id))
        vm.onCategoryChange(food.id)
        vm.onAmountChange("30000")
        assertEquals(-30_000L, vm.state.value.balanceAfterIfNegative) // GoPay opens at 0

        vm.onSave()
        assertTrue(vm.state.value.isDone)
        assertEquals(1, ledger.transactions.count())
    }

    @Test
    fun editingAnExpense_doesNotCountTheOriginalTwice() = runTest {
        ledger.accounts.update(gopay.copy(openingBalanceMinor = 50_000))
        vm.start(CaptureRequest(accountId = gopay.id))
        vm.onCategoryChange(food.id)
        vm.onAmountChange("40000")
        vm.onSave()
        val id = ledger.transactions.allForExport().single().id

        vm.start(CaptureRequest(editing = id))
        vm.onAmountChange("45000")
        assertEquals(null, vm.state.value.balanceAfterIfNegative) // 50,000 − 45,000, not 10,000 − 45,000
        vm.onAmountChange("60000")
        assertEquals(-10_000L, vm.state.value.balanceAfterIfNegative)
    }

    // FR-267: recording stays free of budget judgment. The capture form can't even reach a budget.
    @Test
    fun captureHasNoBudgetDependency() {
        val parameterTypes = CaptureViewModel::class.java.constructors.single().parameterTypes.map { it.simpleName }
        assertTrue(parameterTypes.none { it.contains("Budget") || it.contains("Envelope") })
    }

    private class FakeCaptureContext : CaptureContextStore {
        val state = MutableStateFlow(CaptureContext(null, null))
        override val context: Flow<CaptureContext> = state
        override suspend fun remember(accountId: AccountId, date: Instant) {
            state.value = CaptureContext(accountId, date)
        }
    }
}
