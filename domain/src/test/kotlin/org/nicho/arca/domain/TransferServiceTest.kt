package org.nicho.arca.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.nicho.arca.domain.calculation.BalanceCalculator
import org.nicho.arca.domain.error.DomainError
import org.nicho.arca.domain.error.PersistenceError
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.transfer.TransferDraft
import org.nicho.arca.domain.transfer.TransferIssue
import org.nicho.arca.domain.transfer.TransferService
import org.nicho.arca.testing.InMemoryLedger
import org.nicho.arca.testing.testAccount

// SDD-ARC-001 §12.2 TransferServiceTests · FR-130–133, RSK-07. The bold rows there are the
// injected-failure and delete-with-fee cases: they're what catches an orphaned leg.
class TransferServiceTest {

    private val ledger = InMemoryLedger()
    private val service = TransferService(ledger.transactions, ledger.categories)
    private val bca = testAccount("BCA", openingBalanceMinor = 1_000_000)
    private val gopay = testAccount("GoPay", openingBalanceMinor = 0)

    private fun draft(fee: Long? = null, amount: Long = 500_000) = TransferDraft(
        source = bca,
        destination = gopay,
        amountMinor = amount,
        feeMinor = fee,
        date = NOW,
        note = "top-up",
        now = NOW,
    )

    @Before
    fun setUp() = runTest {
        ledger.categories.seedDefaultsIfNeeded()
        ledger.accounts.insert(bca)
        ledger.accounts.insert(gopay)
    }

    @Test
    fun withoutFee_writesTwoOppositeLegs() = runTest {
        val group = service.createTransfer(draft())

        val members = ledger.transactions.inGroup(group)
        assertEquals(setOf(TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN), members.map { it.type }.toSet())
        val out = members.single { it.type == TransactionType.TRANSFER_OUT }
        val into = members.single { it.type == TransactionType.TRANSFER_IN }
        assertEquals(bca.id, out.accountId)
        assertEquals(gopay.id, out.counterAccountId)
        assertEquals(gopay.id, into.accountId)
        assertEquals(bca.id, into.counterAccountId)
        assertTrue(members.all { it.categoryId == null && it.amountMinor == 500_000L })
    }

    // FR-133 · SDD-ARC-002 §12.2: source −502,500, destination +500,000, net position −2,500.
    @Test
    fun withFee_addsAnExpenseInFees_andMovesBalancesExactly() = runTest {
        val group = service.createTransfer(draft(fee = 2_500))

        val members = ledger.transactions.inGroup(group)
        assertEquals(3, members.size)
        val fee = members.single { it.type == TransactionType.EXPENSE }
        assertEquals(ledger.categories.feesCategory().id, fee.categoryId)
        assertEquals(bca.id, fee.accountId)
        assertNull(fee.counterAccountId)

        val accounts = listOf(bca, gopay)
        val balances = BalanceCalculator.balances(ledger.transactions.observeBalanceRows().first(), accounts)
        assertEquals(497_500L, balances.getValue(bca.id))
        assertEquals(500_000L, balances.getValue(gopay.id))
        assertEquals(997_500L, BalanceCalculator.netPosition(accounts, balances))
    }

    @Test
    fun anInvalidTransfer_writesNothing() = runTest {
        val error = assertThrowsSuspend<DomainError.TransferInvalid> { service.createTransfer(draft().copy(destination = bca)) }

        assertEquals(listOf(TransferIssue.SAME_ACCOUNT), error.issues)
        assertEquals(0, ledger.transactions.count())
    }

    @Test
    fun injectedFailure_twoMembers_leavesZeroRows() = runTest {
        ledger.transactions.failNextWrite = true

        assertThrowsSuspend<PersistenceError.SaveFailed> { service.createTransfer(draft()) }

        assertEquals(0, ledger.transactions.count())
    }

    @Test
    fun injectedFailure_threeMembers_leavesZeroRows() = runTest {
        ledger.transactions.failNextWrite = true

        assertThrowsSuspend<PersistenceError.SaveFailed> { service.createTransfer(draft(fee = 2_500)) }

        assertEquals(0, ledger.transactions.count())
    }

    @Test
    fun deletingAGroupOfThree_leavesNoOrphanedFee() = runTest {
        val group = service.createTransfer(draft(fee = 2_500))

        service.deleteTransfer(group)

        assertEquals(0, ledger.transactions.count())
    }

    @Test
    fun aMissingFeesCategory_failsBeforeWriting() = runTest {
        val bare = InMemoryLedger() // never seeded
        bare.accounts.insert(bca)
        bare.accounts.insert(gopay)

        assertThrowsSuspend<DomainError.FeesCategoryMissing> {
            TransferService(bare.transactions, bare.categories).createTransfer(draft(fee = 2_500))
        }
        assertEquals(0, bare.transactions.count())
    }

    @Test
    fun editingAGroupOfThree_keepsEveryMemberConsistent_andKeepsIds() = runTest {
        val group = service.createTransfer(draft(fee = 2_500))
        val before = ledger.transactions.inGroup(group).associateBy { it.type }

        service.updateTransfer(group, draft(fee = 1_000, amount = 750_000))

        val after = ledger.transactions.inGroup(group).associateBy { it.type }
        assertEquals(750_000L, after.getValue(TransactionType.TRANSFER_OUT).amountMinor)
        assertEquals(750_000L, after.getValue(TransactionType.TRANSFER_IN).amountMinor)
        assertEquals(1_000L, after.getValue(TransactionType.EXPENSE).amountMinor)
        assertEquals(before.mapValues { it.value.id }, after.mapValues { it.value.id })
    }

    @Test
    fun editingAwayTheFee_removesTheFeeRow() = runTest {
        val group = service.createTransfer(draft(fee = 2_500))

        service.updateTransfer(group, draft(fee = null))

        assertEquals(2, ledger.transactions.inGroup(group).size)
    }

    @Test
    fun aFailedEdit_leavesTheOriginalGroupIntact() = runTest {
        val group = service.createTransfer(draft(fee = 2_500))
        ledger.transactions.failNextWrite = true

        assertThrowsSuspend<PersistenceError.SaveFailed> { service.updateTransfer(group, draft(amount = 1)) }

        assertTrue(ledger.transactions.inGroup(group).all { it.amountMinor != 1L })
        assertEquals(3, ledger.transactions.inGroup(group).size)
    }
}
