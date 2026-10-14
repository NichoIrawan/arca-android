package org.nicho.arca.domain.budget

import java.time.YearMonth
import org.nicho.arca.domain.model.Category

// SDD-ARC-002 §7.9 · SDD-ARC-001 DD-37. A budget change, validated by BudgetService before it
// reaches BudgetStore. Kept apart from TransactionDraft on purpose: budget rules must never be
// able to run on the capture path (FR-267).
data class BudgetDraft(
    val category: Category,
    val amountMinor: Long?,   // null = remove the budget from `from` onward
    val from: YearMonth,      // always the current month (FR-265)
)

enum class BudgetIssue {
    AMOUNT_NOT_POSITIVE,      // FR-260: whole rupiah > 0
    SYSTEM_LOCKED_CATEGORY,   // FR-260: Fees is never budgeted
    INCOME_CATEGORY,          // FR-260, DD-A21: income has no envelope
}
