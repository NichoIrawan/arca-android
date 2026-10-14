package org.nicho.arca.domain.model

// SDD-ARC-002 §6.2. Unlike AccountType, an unknown code throws: a guessed transaction type
// would be silently mis-signed in a balance.
enum class TransactionType(val code: String) {
    INCOME("income"),
    EXPENSE("expense"),
    TRANSFER_OUT("transferOut"),
    TRANSFER_IN("transferIn");

    val isTransferPrincipal: Boolean get() = this == TRANSFER_OUT || this == TRANSFER_IN

    val balanceSign: Int
        get() = when (this) {
            INCOME, TRANSFER_IN -> 1
            EXPENSE, TRANSFER_OUT -> -1
        }

    companion object {
        /** SRS §6.3's reporting filter, expressed exactly once (DD-10). */
        val reportable: Set<TransactionType> = setOf(INCOME, EXPENSE)

        fun fromCode(code: String): TransactionType = entries.first { it.code == code }
    }
}
