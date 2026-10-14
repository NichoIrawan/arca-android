package org.nicho.arca.domain.model

import java.time.YearMonth

// SDD-ARC-002 §7.1 · FR-265. The budget in force from `month` onward; null = budget removed
// from that month. A calendar month is a label, not an instant, hence YearMonth.
data class BudgetAllocation(
    val categoryId: CategoryId,
    val month: YearMonth,
    val amountMinor: Long?,
)
