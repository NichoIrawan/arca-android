package org.nicho.arca.domain.budget

import org.nicho.arca.domain.budget.rules.BudgetableCategoryRule
import org.nicho.arca.domain.budget.rules.PositiveWholeRupiahRule
import org.nicho.arca.domain.error.DomainError
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.repository.BudgetStore
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.time.PeriodClock
import org.nicho.arca.domain.validation.ValidationRule

/**
 * SDD-ARC-002 §7.9 · FR-260, FR-261, FR-265. The write path for budgets: validate, then store.
 *
 * A change always applies from the current month forward (FR-265), so closed months keep the
 * budget they had. The editor's helper text says so.
 */
class BudgetService(
    private val categories: CategoryStore,
    private val budgets: BudgetStore,
    private val clock: PeriodClock,
    private val rules: List<ValidationRule<BudgetDraft, BudgetIssue>> = STANDARD_RULES,
) {

    /** [amountMinor] null removes the budget from this month on. Throws DomainError.BudgetInvalid. */
    suspend fun setBudget(categoryId: CategoryId, amountMinor: Long?) {
        val category = categories.byId(categoryId) ?: throw DomainError.NotFound("Category")
        val draft = BudgetDraft(category, amountMinor, from = clock.currentYearMonth())
        val issues = rules.mapNotNull { it.check(draft) }
        if (issues.isNotEmpty()) throw DomainError.BudgetInvalid(issues)
        budgets.setBudget(categoryId, draft.from, amountMinor)
    }

    /** FR-261. The same budgetable-category check; there's no amount to validate. */
    suspend fun setRollsOver(categoryId: CategoryId, rollsOver: Boolean) {
        val category = categories.byId(categoryId) ?: throw DomainError.NotFound("Category")
        val issue = BudgetableCategoryRule.check(BudgetDraft(category, amountMinor = null, from = clock.currentYearMonth()))
        if (issue != null) throw DomainError.BudgetInvalid(listOf(issue))
        budgets.setRollsOver(categoryId, rollsOver)
    }

    companion object {
        val STANDARD_RULES: List<ValidationRule<BudgetDraft, BudgetIssue>> =
            listOf(PositiveWholeRupiahRule, BudgetableCategoryRule)
    }
}
