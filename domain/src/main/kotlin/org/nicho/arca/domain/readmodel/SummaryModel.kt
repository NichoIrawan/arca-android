package org.nicho.arca.domain.readmodel

import java.time.YearMonth
import org.nicho.arca.domain.model.CategorySpend
import org.nicho.arca.domain.model.PeriodSummary
import org.nicho.arca.domain.model.TransactionListItem

// What the Summary (Ringkasan) tab shows: always the current month (DD-22).
data class SummaryModel(
    val month: YearMonth,
    val netPositionMinor: Long,             // FR-221
    val summary: PeriodSummary,             // FR-301
    val categorySpend: List<CategorySpend>, // FR-302, largest first
    val recent: List<TransactionListItem>,
    val isEmpty: Boolean,                   // no transactions at all: the calm empty state (FR-901)
)
