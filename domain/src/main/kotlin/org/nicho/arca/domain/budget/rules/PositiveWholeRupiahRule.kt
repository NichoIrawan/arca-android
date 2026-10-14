package org.nicho.arca.domain.budget.rules

import org.nicho.arca.domain.budget.BudgetDraft
import org.nicho.arca.domain.budget.BudgetIssue
import org.nicho.arca.domain.validation.ValidationRule

// FR-260: a budget is > 0, or null to remove it. "Whole rupiah" needs no check: amounts are Long,
// and CurrencyParser never yields a fraction.
object PositiveWholeRupiahRule : ValidationRule<BudgetDraft, BudgetIssue> {
    override fun check(draft: BudgetDraft): BudgetIssue? {
        val amount = draft.amountMinor ?: return null
        return if (amount <= 0) BudgetIssue.AMOUNT_NOT_POSITIVE else null
    }
}
