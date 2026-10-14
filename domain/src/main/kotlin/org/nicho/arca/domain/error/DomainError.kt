package org.nicho.arca.domain.error

// SDD-ARC-002 §13: errors are grouped by what the user can do about them.
//
// Classes, not the SDD's `sealed interface … : Throwable` (an interface can't extend a class),
// and not `data object`s: a singleton exception keeps the stack trace of wherever it was first
// created, which points every crash report at the wrong place.
//
// ValidationFailed(issues) and TransferInvalid(issues) belong here too. Add them alongside
// ValidationIssue and TransferIssue.
sealed class DomainError(message: String) : Exception(message) {

    /** Fatal configuration: FR-133 can't work without Fees. */
    class FeesCategoryMissing : DomainError("The Fees category is missing")

    /** Blocked action, FR-203: archive the account instead. */
    class AccountHasTransactions : DomainError("The account has transactions")

    /** Blocked action, FR-250 / DD-09: Fees can't be deleted or budgeted. */
    class CategoryIsSystemLocked : DomainError("The category is system-locked")

    /** Blocked action: archive the category instead. Not in the SDD's list; mirrors FR-203. */
    class CategoryHasTransactions : DomainError("The category has transactions")

    /** Blocked action, FR-260 / DD-A21: income categories have no envelope. */
    class CategoryNotBudgetable : DomainError("Only expense categories can have a budget")
}
