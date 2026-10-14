package org.nicho.arca.domain.transfer

import org.nicho.arca.domain.validation.rules.NoteLengthRule

// SDD-ARC-002 §7.4, DD-24. Pure: needs no repository, so it's testable alone and reusable by
// any entry point. Reports every issue at once, like TransactionValidator.
//
// A transfer that drives a balance negative is allowed (FR-132 note): real accounts go
// negative, and refusing to record reality is worse.
object TransferValidator {

    fun validate(draft: TransferDraft): List<TransferIssue> = buildList {
        val source = draft.source
        val destination = draft.destination
        if (source == null) add(TransferIssue.SOURCE_REQUIRED)
        if (destination == null) add(TransferIssue.DESTINATION_REQUIRED)
        if (source != null && destination != null && source.id == destination.id) add(TransferIssue.SAME_ACCOUNT)
        if (source?.isArchived == true || destination?.isArchived == true) add(TransferIssue.ARCHIVED_ACCOUNT)
        if (draft.amountMinor <= 0) add(TransferIssue.AMOUNT_NOT_POSITIVE)
        if (draft.feeMinor != null && draft.feeMinor <= 0) add(TransferIssue.FEE_NOT_POSITIVE)
        if (draft.date.isAfter(draft.now)) add(TransferIssue.FUTURE_DATE)
        val note = draft.note
        if (note != null && note.codePointCount(0, note.length) > NoteLengthRule.MAX) add(TransferIssue.NOTE_TOO_LONG)
    }
}
