package org.nicho.arca.domain.model

// SDD-ARC-002 §7.9, DD-A19 · FR-262. One budgeted category in one month. Every field is
// derived by EnvelopeCalculator and never stored.
data class Envelope(
    val categoryId: CategoryId,
    val budgetMinor: Long,       // the allocation in force this month
    val carryMinor: Long,        // ≥ 0: overspending never carries
    val availableMinor: Long,    // budget + carry
    val spentMinor: Long,
    val leftMinor: Long,         // available − spent; negative ⇒ "Over by |left|"
    val rollsOver: Boolean,
    val status: BudgetStatus,
)
