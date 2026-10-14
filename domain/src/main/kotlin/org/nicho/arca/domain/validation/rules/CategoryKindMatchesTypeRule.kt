package org.nicho.arca.domain.validation.rules

import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.model.TransactionDraft
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.validation.ValidationIssue
import org.nicho.arca.domain.validation.ValidationRule

// DD-A21: an expense needs an expense category and an income an income category. The capture
// form already filters the chips by type, so this is a backstop rather than a message users see.
// A missing category is CategoryPresenceRule's to report, not this rule's.
object CategoryKindMatchesTypeRule : ValidationRule<TransactionDraft, ValidationIssue> {
    override fun check(draft: TransactionDraft): ValidationIssue? {
        val category = draft.category ?: return null
        val expected = when (draft.type) {
            TransactionType.EXPENSE -> CategoryKind.EXPENSE
            TransactionType.INCOME -> CategoryKind.INCOME
            else -> return null
        }
        return if (category.kind != expected) ValidationIssue.CATEGORY_KIND_MISMATCH else null
    }
}
