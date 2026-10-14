package org.nicho.arca.domain.model

import java.time.Instant

// SDD-ARC-002 §6.3. Half-open [start, endExclusive): an instant exactly at the end belongs to
// the next period.
data class Period(
    val start: Instant,
    val endExclusive: Instant,
)
