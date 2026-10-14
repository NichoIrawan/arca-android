package org.nicho.arca.domain.validation.rules

import org.nicho.arca.domain.model.TransactionDraft
import org.nicho.arca.domain.validation.ValidationIssue
import org.nicho.arca.domain.validation.ValidationRule

// DD-11: amounts are always positive; the type gives the direction. A negative amount would
// apply the direction twice.
object PositiveAmountRule : ValidationRule<TransactionDraft, ValidationIssue> {
    override fun check(draft: TransactionDraft): ValidationIssue? =
        if (draft.amountMinor <= 0) ValidationIssue.AMOUNT_NOT_POSITIVE else null
}
