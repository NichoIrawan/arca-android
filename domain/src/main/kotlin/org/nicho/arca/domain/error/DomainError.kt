package org.nicho.arca.domain.error

import org.nicho.arca.domain.budget.BudgetIssue
import org.nicho.arca.domain.transfer.TransferIssue
import org.nicho.arca.domain.validation.ValidationIssue

// SDD-ARC-002 §13: errors are grouped by what the user can do about them.
sealed class DomainError(message: String) : Exception(message) {

    // ── User-correctable: an inline message on the field; nothing was written ──

    class ValidationFailed(val issues: List<ValidationIssue>) : DomainError("Invalid transaction: $issues")

    class TransferInvalid(val issues: List<TransferIssue>) : DomainError("Invalid transfer: $issues")

    class BudgetInvalid(val issues: List<BudgetIssue>) : DomainError("Invalid budget: $issues")

    // ── Fatal configuration ──

    /** FR-133 can't work without Fees. */
    class FeesCategoryMissing : DomainError("The Fees category is missing")

    // ── Blocked action: explain, and offer the permitted alternative ──

    /** FR-203: archive the account instead. */
    class AccountHasTransactions : DomainError("The account has transactions")

    /** FR-250 / DD-09: Fees can't be deleted or budgeted. */
    class CategoryIsSystemLocked : DomainError("The category is system-locked")

    /** Archive the category instead. Not in the SDD's list; mirrors FR-203. */
    class CategoryHasTransactions : DomainError("The category has transactions")

    /** FR-260 / DD-A21: income categories have no envelope. */
    class CategoryNotBudgeTable : DomainError("Only expense categories can have a budget")

    /** The record an edit or delete refers to no longer exists. */
    class NotFound(what: String) : DomainError("$what not found")
}
