package org.nicho.arca.domain.model

// SDD-ARC-002 §7.1 · FR-951–953. Derived, never persisted. It still carries a code (DD-A08)
// because the iOS client must use the same values when the mascot reads it.
enum class BudgetStatus(val code: String) {
    NO_BUDGET_SET("noBudgetSet"),
    UNDER_BUDGET("underBudget"),
    NEAR_LIMIT("nearLimit"),     // ≥ 90 % and ≤ 100 % used
    OVER_BUDGET("overBudget"),   // > 100 % used
}
