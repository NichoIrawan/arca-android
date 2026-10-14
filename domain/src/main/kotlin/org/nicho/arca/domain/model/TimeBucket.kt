package org.nicho.arca.domain.model

import java.time.LocalDate

// FR-303. Daily buckets within a month, monthly across a year.
enum class Granularity { DAY, MONTH }

/** Expenditure in one bucket. [start] is the bucket's first day (the 1st, for MONTH). */
data class TimeBucket(
    val start: LocalDate,
    val totalMinor: Long,
)
