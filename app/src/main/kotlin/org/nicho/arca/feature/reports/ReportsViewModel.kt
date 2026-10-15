package org.nicho.arca.feature.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.nicho.arca.domain.readmodel.ObserveReports
import org.nicho.arca.domain.readmodel.ReportMetric
import org.nicho.arca.domain.readmodel.ReportRange
import org.nicho.arca.domain.readmodel.ReportSelection
import org.nicho.arca.domain.readmodel.TrendGranularity
import org.nicho.arca.domain.time.PeriodClock
import org.nicho.arca.export.CsvExporter
import org.nicho.arca.export.ExportResult
import org.nicho.arca.navigation.ReportsRoute

/** How far back an export reaches (the export sheet's three choices). */
enum class ExportSpan { THIS_MONTH, LAST_3_MONTHS, ALL }

sealed interface ExportState {
    data object Idle : ExportState
    data object Working : ExportState
    data class Done(val result: ExportResult) : ExportState
    data object Failed : ExportState
}

/**
 * Reports (FR-301–306). Holds the range (7 days, 28 days, or a month), the trend's grain, and
 * what's measured: expenses, income or the net (client request, 2026-10-15).
 * Opened for the month in progress it starts on the last 28 days, as the prototype does; opened
 * for an earlier month (Budget's "Spending report") it starts on that month.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeReports: ObserveReports,
    private val clock: PeriodClock,
    private val exporter: CsvExporter,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ReportsRoute>()
    private val routeMonth = YearMonth.of(route.year, route.month)
    private val selection = MutableStateFlow(
        ReportSelection(
            if (routeMonth == clock.currentYearMonth()) ReportRange.LastDays(28) else ReportRange.Month(routeMonth),
            TrendGranularity.WEEKLY,
        ),
    )

    // What's measured is the screen's choice alone: the model carries all three, so switching is instant.
    private val metric = MutableStateFlow(ReportMetric.EXPENSES)

    val state: StateFlow<ReportsUiState> = combine(observeReports(selection), metric) { model, measured -> ReportsUiState.Ready(model, measured) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState.Loading)

    fun onMetricChange(measured: ReportMetric) {
        metric.value = measured
    }

    private val _export = MutableStateFlow<ExportState>(ExportState.Idle)
    val export: StateFlow<ExportState> = _export.asStateFlow()

    fun onRangeChange(range: ReportRange) {
        selection.value = selection.value.copy(range = range)
    }

    fun onGranularityChange(granularity: TrendGranularity) {
        selection.value = selection.value.copy(granularity = granularity)
    }

    /** Writes the chosen months, ending with [month], to Downloads. */
    fun onExport(span: ExportSpan, month: YearMonth, earliest: YearMonth?) {
        if (_export.value == ExportState.Working) return
        val from = when (span) {
            ExportSpan.THIS_MONTH -> month
            ExportSpan.LAST_3_MONTHS -> month.minusMonths(2)
            ExportSpan.ALL -> earliest ?: month
        }
        val name = when (span) {
            ExportSpan.THIS_MONTH -> "arca-$month.csv"
            ExportSpan.LAST_3_MONTHS -> "arca-$from-to-${month.monthValue.toString().padStart(2, '0')}.csv"
            ExportSpan.ALL -> "arca-all-data.csv"
        }
        _export.value = ExportState.Working
        viewModelScope.launch {
            _export.value = runCatching { exporter.export(from, month, name) }
                .fold({ ExportState.Done(it) }, { ExportState.Failed })
        }
    }

    fun onExportDismissed() {
        _export.value = ExportState.Idle
    }
}
