package org.nicho.arca.domain.validation.rules

import org.nicho.arca.domain.model.TransactionDraft
import org.nicho.arca.domain.validation.ValidationIssue
import org.nicho.arca.domain.validation.ValidationRule

// FR-103: any past date is fine; a future one would be a scheduled event, not a record, and
// would put a balance ahead of reality. "Now" comes from the draft, never the system clock.
object NotFutureDatedRule : ValidationRule<TransactionDraft, ValidationIssue> {
    override fun check(draft: TransactionDraft): ValidationIssue? =
        if (draft.date.isAfter(draft.now)) ValidationIssue.FUTURE_DATE else null
}
