package org.nicho.arca.feature.capture

import java.time.LocalDate
import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.calculation.BalanceCalculator
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.BalanceRow
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.parsing.CurrencyParser

/** Everything the form can complain about, as one list the screen maps to messages. */
enum class CaptureIssue {
    AMOUNT_EMPTY,
    AMOUNT_UNCLEAR,        // "Jumlah tidak jelas — coba 50000, 50rb, atau 1,5jt"
    AMOUNT_NOT_POSITIVE,
    AMOUNT_TOO_LARGE,
    ACCOUNT_REQUIRED,
    ACCOUNT_ARCHIVED,
    CATEGORY_REQUIRED,
    CATEGORY_MISMATCH,
    FUTURE_DATE,
    NOTE_TOO_LONG,
    SAME_ACCOUNT,
    DESTINATION_REQUIRED,
    FEE_INVALID,
    SAVE_FAILED,
}

data class CaptureUiState(
    val mode: CaptureMode = CaptureMode.EXPENSE,
    val isEditing: Boolean = false,
    val amountText: String = "",
    val amountMinor: Long? = null,
    val accounts: List<Account> = emptyList(),     // active only; favourites first
    val accountId: AccountId? = null,
    val categories: List<Category> = emptyList(),  // active, of the mode's kind (DD-A21)
    val categoryId: CategoryId? = null,
    val destinationId: AccountId? = null,          // transfers: accountId is the source
    val feeText: String = "",
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val issues: Set<CaptureIssue> = emptySet(),
    val isSaving: Boolean = false,
    val isDone: Boolean = false,
    val balances: Map<AccountId, Long> = emptyMap(),   // every account now, for the warning below
    val replacedRows: List<BalanceRow> = emptyList(),  // an edit's original rows, taken out first
) {
    val isTransfer: Boolean get() = mode == CaptureMode.TRANSFER

    /**
     * The paying account's balance after this save, when that's below zero (client decision,
     * 2026-10-14): a warning shown before saving, never a block (SRS FR-132's note). Expenses and
     * transfers only; the domain does the arithmetic.
     */
    val balanceAfterIfNegative: Long?
        get() {
            if (mode == CaptureMode.INCOME) return null
            val id = accountId ?: return null
            val amount = amountMinor ?: return null
            val current = balances[id] ?: return null
            val added = if (mode == CaptureMode.TRANSFER) {
                listOfNotNull(
                    BalanceRow(id, amount, TransactionType.TRANSFER_OUT),
                    CurrencyParser.parseOrNull(feeText)?.let { BalanceRow(id, it, TransactionType.EXPENSE) },
                )
            } else {
                listOf(BalanceRow(id, amount, TransactionType.EXPENSE))
            }
            val after = BalanceCalculator.projected(current, added, replacedRows.filter { it.accountId == id })
            return after.takeIf(BalanceCalculator::isNegative)
        }
    fun account(id: AccountId?): Account? = accounts.firstOrNull { it.id == id }

    companion object {
        fun issueFor(error: CurrencyParser.ParseError): CaptureIssue = when (error) {
            CurrencyParser.ParseError.EMPTY -> CaptureIssue.AMOUNT_EMPTY
            CurrencyParser.ParseError.NOT_A_NUMBER, CurrencyParser.ParseError.AMBIGUOUS_SEPARATOR -> CaptureIssue.AMOUNT_UNCLEAR
            CurrencyParser.ParseError.NOT_POSITIVE -> CaptureIssue.AMOUNT_NOT_POSITIVE
            CurrencyParser.ParseError.TOO_LARGE -> CaptureIssue.AMOUNT_TOO_LARGE
        }
    }
}
