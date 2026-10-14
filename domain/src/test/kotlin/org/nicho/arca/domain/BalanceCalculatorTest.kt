package org.nicho.arca.domain

import java.math.BigInteger
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.nicho.arca.domain.calculation.BalanceCalculator
import org.nicho.arca.domain.model.AccountType
import org.nicho.arca.domain.model.BalanceRow
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.testing.testAccount
import org.nicho.arca.testing.testCategory
import org.nicho.arca.testing.testExpense
import org.nicho.arca.testing.testIncome
import org.nicho.arca.testing.testTransfer

// SDD-ARC-001 §12.2 BalanceCalculatorTests · SDD-ARC-002 §14.2 (NFR-R03).
class BalanceCalculatorTest {

    private val bca = testAccount("BCA", openingBalanceMinor = 1_000_000)
    private val gopay = testAccount("GoPay", type = AccountType.EWALLET, openingBalanceMinor = 50_000)
    private val accounts = listOf(bca, gopay)
    private val food = testCategory()
    private val salary = testCategory("Gaji")
    private val fees = testCategory("Biaya", isSystemLocked = true)
    private val day = at(2026, 10, 1)

    private fun rows(txs: List<Transaction>) = txs.map { BalanceRow(it.accountId, it.amountMinor, it.type) }
    private fun balances(txs: List<Transaction>) = BalanceCalculator.balances(rows(txs), accounts)
    private fun net(txs: List<Transaction>) = BalanceCalculator.netPosition(accounts, balances(txs))

    @Test
    fun emptyLedger_isTheOpeningBalance() {
        assertEquals(mapOf(bca.id to 1_000_000L, gopay.id to 50_000L), balances(emptyList()))
    }

    @Test
    fun incomeAddsAndExpenseSubtracts() {
        val txs = listOf(testIncome(bca, salary, 500_000, day), testExpense(bca, food, 75_000, day))
        assertEquals(1_425_000L, balances(txs).getValue(bca.id))
    }

    @Test
    fun transfer_movesMoney_andLeavesNetPositionUnchanged() {
        val txs = testTransfer(bca, gopay, 200_000, day)
        assertEquals(800_000L, balances(txs).getValue(bca.id))
        assertEquals(250_000L, balances(txs).getValue(gopay.id))
        assertEquals(net(emptyList()), net(txs))
    }

    // FR-133: source −(amount + fee), destination +amount, net position −fee.
    @Test
    fun transferWithFee_lowersNetPositionByExactlyTheFee() {
        val txs = testTransfer(bca, gopay, 500_000, day, fee = 2_500L to fees)
        assertEquals(1_000_000L - 502_500L, balances(txs).getValue(bca.id))
        assertEquals(550_000L, balances(txs).getValue(gopay.id))
        assertEquals(net(emptyList()) - 2_500L, net(txs))
    }

    // FR-132 note: real accounts go negative; the calculator doesn't clamp.
    @Test
    fun aNegativeBalance_isReportedAsIs() {
        val txs = listOf(testExpense(gopay, food, 80_000, day))
        assertEquals(-30_000L, balances(txs).getValue(gopay.id))
    }

    @Test
    fun archivedAccounts_areLeftOutOfNetPositionAndSubtotals() {
        val old = testAccount("Old", openingBalanceMinor = 9_999_999, isArchived = true)
        val all = accounts + old
        val balances = BalanceCalculator.balances(emptyList(), all)
        assertEquals(1_050_000L, BalanceCalculator.netPosition(all, balances))
        assertEquals(
            mapOf(AccountType.BANK to 1_000_000L, AccountType.EWALLET to 50_000L),
            BalanceCalculator.subtotalsByType(all, balances),
        )
    }

    @Test
    fun rowsForAccountsNotInTheList_areIgnored() {
        val stranger = testAccount("Elsewhere")
        assertEquals(balances(emptyList()), balances(listOf(testExpense(stranger, food, 10_000, day))))
    }

    // Kotlin's Int stops at about Rp 2.1 billion (DD-A08).
    @Test
    fun balancesPastIntMax_areExact() {
        val txs = listOf(testIncome(bca, salary, 3_000_000_000, day))
        assertEquals(3_001_000_000L, balances(txs).getValue(bca.id))
    }

    @Test
    fun overflow_throwsInsteadOfWrapping() {
        val rich = testAccount(openingBalanceMinor = Long.MAX_VALUE)
        assertThrows(ArithmeticException::class.java) {
            BalanceCalculator.balances(rows(listOf(testIncome(rich, salary, 1, day))), listOf(rich))
        }
    }

    // A ledger far bigger than anyone would check by hand, against BigInteger arithmetic that
    // shares no code with the calculator.
    @Test
    fun fullLedgerFold_matchesAnIndependentComputation() {
        val random = Random(42)
        val txs = (1..20_000).flatMap {
            val amount = random.nextLong(1_000, 5_000_000)
            when (random.nextInt(4)) {
                0 -> listOf(testIncome(bca, salary, amount, day))
                1 -> listOf(testExpense(gopay, food, amount, day))
                2 -> testTransfer(gopay, bca, amount, day)
                else -> testTransfer(bca, gopay, amount, day, fee = 2_500L to fees)
            }
        }
        val expected = accounts.associate { account ->
            var total = BigInteger.valueOf(account.openingBalanceMinor)
            for (tx in txs.filter { it.accountId == account.id }) {
                val amount = BigInteger.valueOf(tx.amountMinor)
                total = if (tx.type == TransactionType.INCOME || tx.type == TransactionType.TRANSFER_IN) total + amount else total - amount
            }
            account.id to total.longValueExact()
        }
        assertEquals(expected, balances(txs))
    }
}
