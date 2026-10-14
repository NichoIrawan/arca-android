package org.nicho.arca.data.db.entities

import androidx.room.Embedded

// Read projections: narrower or joined shapes of the tables above. Not entities, so they add
// no tables. None of them carries a computed amount (DD-A10).

/** The three columns a balance fold needs, for every account at once (SDD-ARC-002 §6.4). */
internal data class BalanceRowEntity(
    val accountId: String,
    val amountMinor: Long,
    val type: String,
)

/** A list row: the transaction plus the display fields of what it points at (§8.1). */
internal data class TransactionListItemRow(
    @Embedded val transaction: TransactionEntity,
    val accountName: String,
    val accountColor: String,
    val counterAccountName: String?,     // transfers only: "BCA → GoPay"
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryColor: String?,
)
