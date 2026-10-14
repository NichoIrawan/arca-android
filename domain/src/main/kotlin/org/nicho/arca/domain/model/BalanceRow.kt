package org.nicho.arca.domain.model

// SDD-ARC-002 §6.4, DD-A10. The narrow projection BalanceCalculator folds over.
data class BalanceRow(
    val accountId: AccountId,
    val amountMinor: Long,
    val type: TransactionType,
)
