package org.nicho.arca.domain.validation

// DD-26. One rule, one file, one test. A new rule is a new class added to a list; no shared
// function is edited. Generic over the draft and its issue type, so capture rules
// (TransactionDraft → ValidationIssue) and budget rules (BudgetDraft → BudgetIssue) can't be
// mixed up: FR-267 forbids budget logic on the capture path, and the type system enforces it.
fun interface ValidationRule<in D, out I> {
    /** The problem with [draft], or null if this rule is satisfied. */
    fun check(draft: D): I?
}
