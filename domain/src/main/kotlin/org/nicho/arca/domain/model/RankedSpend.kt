package org.nicho.arca.domain.model

// The Overview's and Reports' category bars: the largest few, then one "Other" for the rest.
data class RankedSpend(
    val top: List<CategorySpend>,
    val rest: RestSpend?,
)

/** Everything after the top categories, as one bar. [share] is geometry only (DD-12). */
data class RestSpend(
    val totalMinor: Long,
    val share: Float,
    val categoryIds: List<CategoryId>,
)
