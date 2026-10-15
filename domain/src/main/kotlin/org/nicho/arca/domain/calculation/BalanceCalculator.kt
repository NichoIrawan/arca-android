package org.nicho.arca.domain.calculation

import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.AccountType
import java.time.Instant
import org.nicho.arca.domain.model.BalanceRow
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionType

/**
 * SDD-ARC-002 §7.3, DD-21. One of the three types allowed to do money arithmetic; the others
 * are ReportAggregator and EnvelopeCalculator. Nothing else adds, subtracts or folds an amount:
 * not a ViewModel, not a widget, not a SQL query.
 *
 * Pure functions over values: the stores fetch, this computes. Overflow throws
 * ArithmeticException (Math.addExact) instead of wrapping to a wrong balance (§6.3).
 */
object BalanceCalculator {

    /**
     * FR-220: each account's opening balance plus its signed rows. A fee row is an ordinary
     * expense, so it needs no special case. Every account in [accounts] gets an entry, even with
     * no rows. Rows for accounts not in [accounts] are ignored.
     */
    fun balances(rows: List<BalanceRow>, accounts: List<Account>): Map<AccountId, Long> {
        val running = HashMap<AccountId, Long>(accounts.size * 2)
        for (account in accounts) running[account.id] = account.openingBalanceMinor
        for (row in rows) {
            val current = running[row.accountId] ?: continue
            running[row.accountId] = Math.addExact(current, signed(row))
        }
        return running
    }

    /**
     * FR-221: the sum of non-archived balances. An archived account's history still counts in
     * reports (FR-202); its balance doesn't count toward "how much do I have".
     */
    fun netPosition(accounts: List<Account>, balances: Map<AccountId, Long>): Long {
        var total = 0L
        for (account in accounts) {
            if (account.isArchived) continue
            total = Math.addExact(total, balances[account.id] ?: 0L)
        }
        return total
    }

    /** FR-204: per-type subtotals for the grouped account list, non-archived accounts only. */
    fun subtotalsByType(accounts: List<Account>, balances: Map<AccountId, Long>): Map<AccountType, Long> {
        val totals = LinkedHashMap<AccountType, Long>()
        for (account in accounts) {
            if (account.isArchived) continue
            val current = totals[account.type] ?: 0L
            totals[account.type] = Math.addExact(current, balances[account.id] ?: 0L)
        }
        return totals
    }

    /**
     * Each type's share of the money held, for the Accounts composition bar ("Bank 96%"). Only
     * positive subtotals count: a negative balance owes money rather than holding it. Shares are
     * geometry (DD-12) and sum to 1; empty when nothing positive is held.
     */
    fun composition(subtotals: Map<AccountType, Long>): Map<AccountType, Float> {
        val held = subtotals.filterValues { it > 0 }
        var total = 0L
        for (value in held.values) total = Math.addExact(total, value)
        if (total == 0L) return emptyMap()
        return held.mapValues { (_, value) -> value.toFloat() / total.toFloat() }
    }

    /**
     * The row that takes an account from [currentMinor] to the [targetMinor] the user counted:
     * an ADJUSTMENT_IN or ADJUSTMENT_OUT for the (always positive) difference, or null when the
     * two already agree.
     */
    fun adjustmentTo(currentMinor: Long, targetMinor: Long): Pair<TransactionType, Long>? {
        val difference = Math.subtractExact(targetMinor, currentMinor)
        return when {
            difference > 0 -> TransactionType.ADJUSTMENT_IN to difference
            difference < 0 -> TransactionType.ADJUSTMENT_OUT to Math.negateExact(difference)
            else -> null
        }
    }

    /**
     * An account's balance if [removed] rows were taken away and [added] rows recorded: what the
     * capture form checks before an expense or transfer takes an account below zero. [removed] is
     * the original of an edit, so editing a row isn't counted twice.
     */
    fun projected(balanceMinor: Long, added: List<BalanceRow>, removed: List<BalanceRow> = emptyList()): Long {
        var total = balanceMinor
        for (row in removed) total = Math.subtractExact(total, signed(row))
        for (row in added) total = Math.addExact(total, signed(row))
        return total
    }

    /**
     * An account's balance at the end of each period in [ends] (exclusive instants): the opening
     * balance plus every row of [txs] dated before that end. For Account Detail's balance trend.
     * [txs] must all belong to the one account and [ends] must ascend; one entry per end, in order.
     */
    fun balancesAt(openingMinor: Long, txs: List<Transaction>, ends: List<Instant>): List<Long> {
        require(ends.zipWithNext().all { (a, b) -> a <= b }) { "ends must be in ascending order" }
        val sorted = txs.sortedBy { it.date }
        val result = ArrayList<Long>(ends.size)
        var total = openingMinor
        var next = 0
        for (end in ends) {
            while (next < sorted.size && sorted[next].date < end) {
                val tx = sorted[next++]
                total = Math.addExact(total, Math.multiplyExact(tx.amountMinor, tx.type.balanceSign.toLong()))
            }
            result += total
        }
        return result
    }

    /** Whole-percent change between two balances, for the trend readout; null from zero. */
    fun changePercent(previousMinor: Long, currentMinor: Long): Int? {
        if (previousMinor == 0L) return null
        val change = Math.multiplyExact(Math.subtractExact(currentMinor, previousMinor), 100L) / Math.absExact(previousMinor)
        return Math.toIntExact(change)
    }

    /** Whether a balance is below zero: the "Negative balance" cue (FR-222). */
    fun isNegative(balanceMinor: Long): Boolean = balanceMinor < 0

    private fun signed(row: BalanceRow): Long = Math.multiplyExact(row.amountMinor, row.type.balanceSign.toLong())
}
