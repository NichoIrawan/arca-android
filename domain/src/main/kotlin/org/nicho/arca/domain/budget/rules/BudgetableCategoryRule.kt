package org.nicho.arca.domain.budget.rules

import org.nicho.arca.domain.budget.BudgetDraft
import org.nicho.arca.domain.budget.BudgetIssue
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.validation.ValidationRule

// FR-260, DD-A21: only expense categories that aren't system-locked have envelopes. The UI offers
// no "Set budget" on Fees or income, so this is the first of three guards; BudgetStore and the
// DAO's WHERE clause are the others.
object BudgetableCategoryRule : ValidationRule<BudgetDraft, BudgetIssue> {
    override fun check(draft: BudgetDraft): BudgetIssue? = when {
        draft.category.isSystemLocked -> BudgetIssue.SYSTEM_LOCKED_CATEGORY
        draft.category.kind == CategoryKind.INCOME -> BudgetIssue.INCOME_CATEGORY
        else -> null
    }
}
