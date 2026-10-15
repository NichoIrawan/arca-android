package org.nicho.arca.domain.model

// SDD-ARC-002 §6.2. Unlike AccountType, an unknown code throws: a guessed transaction type
// would be silently mis-signed in a balance.
enum class TransactionType(val code: String) {
    INCOME("income"),
    EXPENSE("expense"),
    TRANSFER_OUT("transferOut"),
    TRANSFER_IN("transferIn"),

    /**
     * A balance adjustment (added 2026-10-14, client request): the difference between the balance
     * Arca computed and the one the user counted, recorded as a dated row on that account. Like a
     * transfer it moves the balance but is neither income nor spending, so no report counts it,
     * and it has no category.
     */
    ADJUSTMENT_IN("adjustmentIn"),
    ADJUSTMENT_OUT("adjustmentOut");

    val isTransferPrincipal: Boolean get() = this == TRANSFER_OUT || this == TRANSFER_IN

    val isAdjustment: Boolean get() = this == ADJUSTMENT_IN || this == ADJUSTMENT_OUT

    val balanceSign: Int
        get() = when (this) {
            INCOME, TRANSFER_IN, ADJUSTMENT_IN -> 1
            EXPENSE, TRANSFER_OUT, ADJUSTMENT_OUT -> -1
        }

    companion object {
        /** SRS §6.3's reporting filter, expressed exactly once (DD-10). */
        val reportable: Set<TransactionType> = setOf(INCOME, EXPENSE)

        fun fromCode(code: String): TransactionType = entries.first { it.code == code }
    }
}
