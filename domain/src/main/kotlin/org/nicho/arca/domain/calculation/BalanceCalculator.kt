package org.nicho.arca.domain.calculation

import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.AccountType
import org.nicho.arca.domain.model.BalanceRow

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

    private fun signed(row: BalanceRow): Long = Math.multiplyExact(row.amountMinor, row.type.balanceSign.toLong())
}
