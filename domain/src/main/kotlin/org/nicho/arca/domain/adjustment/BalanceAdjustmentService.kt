package org.nicho.arca.domain.adjustment

import java.time.Instant
import kotlinx.coroutines.flow.first
import org.nicho.arca.domain.calculation.BalanceCalculator
import org.nicho.arca.domain.error.DomainError
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionId
import org.nicho.arca.domain.repository.AccountReading
import org.nicho.arca.domain.repository.TransactionReading
import org.nicho.arca.domain.repository.TransactionWriting

/**
 * "Adjust balance" (client request, 2026-10-14): the user types the balance they actually have,
 * and Arca records the difference as one dated ADJUSTMENT row on that account.
 *
 * The opening balance stays locked (the prototype's rule, "so your history stays accurate"): an
 * adjustment changes the balance from its date onwards and leaves every earlier balance as it
 * was. It is neither income nor spending, so no report or budget sees it.
 *
 * The current balance is computed here from the store, not passed in, so a screen showing a stale
 * figure can't record the wrong difference.
 */
class BalanceAdjustmentService(
    private val reading: TransactionReading,
    private val writing: TransactionWriting,
    private val accounts: AccountReading,
) {

    /** The account's balance now, as the adjustment will see it. */
    suspend fun currentBalance(accountId: AccountId): Long {
        val account = accounts.byId(accountId) ?: throw DomainError.NotFound("Account $accountId")
        val rows = reading.observeBalanceRows().first()
        return BalanceCalculator.balances(rows, listOf(account)).getValue(accountId)
    }

    /**
     * Records what takes [accountId] to [targetMinor] (which may be zero or negative: an overdraft
     * is a real balance). Returns the row written, or null when the balance already matches.
     */
    suspend fun adjust(accountId: AccountId, targetMinor: Long, at: Instant, note: String?): Transaction? {
        val (type, amount) = BalanceCalculator.adjustmentTo(currentBalance(accountId), targetMinor) ?: return null
        val tx = Transaction(
            id = TransactionId.random(),
            amountMinor = amount,
            type = type,
            date = at,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            accountId = accountId,
            categoryId = null,
            transferGroupId = null,
            counterAccountId = null,
        )
        writing.insert(tx)
        return tx
    }
}
