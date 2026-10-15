package org.nicho.arca.domain.readmodel

import java.time.YearMonth
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.Envelope
import org.nicho.arca.domain.model.CategorySpend
import org.nicho.arca.domain.model.EnvelopeSummary
import org.nicho.arca.domain.model.PeriodSummary
import org.nicho.arca.domain.model.RankedSpend
import org.nicho.arca.domain.model.TransactionListItem

// What the Overview (Ringkasan) tab shows: always the current month (DD-22).
data class SummaryModel(
    val month: YearMonth,
    val netPositionMinor: Long,             // FR-221, the hero
    val summary: PeriodSummary,             // FR-301: In / Out / Net
    val budget: EnvelopeSummary,            // "Left to spend" and the envelopes needing attention
    val leftToSpendFraction: Float,         // the hero's drain bar, geometry only
    val needsAttention: List<Envelope>,     // over first, then the emptiest; at most three
    val previousMonthExpenseMinor: Long,
    val expenseChangePercent: Int?,         // vs last month, for the insight card; null if no data
    val categorySpend: List<CategorySpend>, // FR-302, largest first
    val categoryBars: RankedSpend,          // the four largest and one "Other" for the rest
    val recent: List<TransactionListItem>,
    val categories: Map<CategoryId, Category>, // display fields for envelopes, archived included
    val isEmpty: Boolean,                   // no transactions at all: the calm empty state (FR-901)
)
