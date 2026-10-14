package org.nicho.arca.domain.validation.rules

import org.nicho.arca.domain.model.TransactionDraft
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.validation.ValidationIssue
import org.nicho.arca.domain.validation.ValidationRule

// SRS §6.1: income and expense need a category. Transfer principals carry none, and are
// validated by TransferValidator anyway.
object CategoryPresenceRule : ValidationRule<TransactionDraft, ValidationIssue> {
    override fun check(draft: TransactionDraft): ValidationIssue? =
        if (draft.type in TransactionType.reportable && draft.category == null) ValidationIssue.CATEGORY_REQUIRED else null
}
