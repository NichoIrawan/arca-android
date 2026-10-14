package org.nicho.arca.domain.model

import java.time.Instant

// SDD-ARC-002 §7.1 · SRS §6.1. amountMinor is always > 0; direction comes from type (DD-11).
// Only TransferService builds a Transaction with a transferGroupId (DD-06).
data class Transaction(
    val id: TransactionId,
    val amountMinor: Long,
    val type: TransactionType,
    val date: Instant,
    val note: String?,
    val accountId: AccountId,
    val categoryId: CategoryId?,
    val transferGroupId: TransferGroupId?,
    val counterAccountId: AccountId?,
)
