package org.nicho.arca.domain.validation.rules

import org.nicho.arca.domain.model.TransactionDraft
import org.nicho.arca.domain.validation.ValidationIssue
import org.nicho.arca.domain.validation.ValidationRule

// FR-202: an archived account takes no new transactions. It may still appear in history.
object ActiveAccountRule : ValidationRule<TransactionDraft, ValidationIssue> {
    override fun check(draft: TransactionDraft): ValidationIssue? = when {
        draft.account == null -> ValidationIssue.ACCOUNT_REQUIRED
        draft.account.isArchived -> ValidationIssue.ACCOUNT_ARCHIVED
        else -> null
    }
}
