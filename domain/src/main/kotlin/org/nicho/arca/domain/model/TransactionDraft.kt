package org.nicho.arca.domain.model

import java.time.Instant

// SDD-ARC-002 §7.6, DD-26. What the capture form validates before an income or expense is
// saved. Transfers have their own draft (TransferDraft).
//
// `now` travels on the draft so NotFutureDatedRule never reads the system clock, and tests
// never have to freeze it. account and category are nullable because a half-filled form is a
// normal state; the rules report what's missing.
data class TransactionDraft(
    val amountMinor: Long,
    val type: TransactionType,
    val date: Instant,
    val account: Account?,
    val category: Category?,
    val note: String?,
    val now: Instant,
)
