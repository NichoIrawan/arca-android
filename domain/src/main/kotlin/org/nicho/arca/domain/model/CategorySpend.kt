package org.nicho.arca.domain.model

// SDD-ARC-002 §7.3 · FR-302, NFR-A04. Carries the icon so a legend can't be built without it:
// colour is never the only signal. `share` (0..1) is geometry for the chart, so it's a Float and
// exempt from the no-floating-point rule (DD-12); never add or compare money through it.
data class CategorySpend(
    val categoryId: CategoryId,
    val name: String,
    val icon: String,
    val colorHex: String,
    val totalMinor: Long,
    val share: Float,
)
