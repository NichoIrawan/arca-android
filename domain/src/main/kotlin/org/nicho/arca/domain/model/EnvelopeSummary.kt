package org.nicho.arca.domain.model

import java.time.YearMonth

// SDD-ARC-002 §7.9 · FR-263, FR-266.
data class EnvelopeSummary(
    val month: YearMonth,
    val envelopes: List<Envelope>,
    val notBudgeted: List<CategorySpend>,   // FR-266, Fees included; neutral, never negative styling
    val totalAvailableMinor: Long,          // FR-263: Σ available; read-only
    val totalSpentInEnvelopesMinor: Long,
    val totalLeftMinor: Long,
    val totalStatus: BudgetStatus,          // what summary surfaces pass to the mascot (§16.2)
    val allSpendingMinor: Long,             // envelopes + not budgeted, so the screen can't under-report
)
