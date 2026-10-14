package org.nicho.arca.domain.readmodel

import java.time.YearMonth
import org.nicho.arca.domain.model.CategorySpend
import org.nicho.arca.domain.model.PeriodSummary
import org.nicho.arca.domain.model.TimeBucket

// What the Reports (Laporan) screen shows for one month (FR-301–304).
data class ReportsModel(
    val month: YearMonth,
    val summary: PeriodSummary,
    val spendByCategory: List<CategorySpend>, // largest first
    val dailySpend: List<TimeBucket>,         // one bucket per day of the month, empty days included
)
