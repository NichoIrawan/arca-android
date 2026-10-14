package org.nicho.arca.domain.model

// SDD-ARC-002 §8.1, DD-A04. A list row: the transaction plus the display fields of the
// account and category it points at. Display only; carries no computed amount.
data class TransactionListItem(
    val transaction: Transaction,
    val accountName: String,
    val accountColorHex: String,
    val counterAccountName: String?,     // transfer principals only
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryColorHex: String?,
)
