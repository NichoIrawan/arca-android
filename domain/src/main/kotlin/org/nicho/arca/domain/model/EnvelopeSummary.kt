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
    val carriedInMinor: Long = 0,           // Σ carry: "incl. Rp480,000 rolled over"
    val rollsOutMinor: Long = 0,            // what roll-over envelopes have left, carried into next month
    val resetsMinor: Long = 0,              // what the other envelopes have left, which resets
    val overspentMinor: Long = 0,           // Σ over-by; overspending is never carried
)
