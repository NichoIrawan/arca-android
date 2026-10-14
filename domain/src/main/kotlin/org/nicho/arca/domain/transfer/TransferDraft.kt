package org.nicho.arca.domain.transfer

import java.time.Instant
import org.nicho.arca.domain.model.Account

// FR-130–133. What the transfer form validates. feeMinor is null for "no fee"; the form maps an
// empty fee field to null, never to 0. Accounts are nullable because a half-filled form is a
// normal state.
data class TransferDraft(
    val source: Account?,
    val destination: Account?,
    val amountMinor: Long,
    val feeMinor: Long?,
    val date: Instant,
    val note: String?,
    val now: Instant,
)
