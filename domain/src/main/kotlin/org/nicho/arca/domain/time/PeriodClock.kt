package org.nicho.arca.domain.time

import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.nicho.arca.domain.model.Period

/** A calendar month plus its instant boundaries in the zone that was current when computed. */
data class CalendarMonth(
    val month: YearMonth,
    val period: Period,
)

/**
 * SDD-ARC-002 DD-A18. The one place that turns "now" into "this month".
 *
 * Instants are stored in UTC; month boundaries are computed in the *current* system zone as
 * half-open [1st 00:00, next 1st 00:00) intervals.
 *
 * :domain can't listen for Android broadcasts, so :app passes [changes]: a Flow that emits on
 * resume, ACTION_DATE_CHANGED and ACTION_TIMEZONE_CHANGED. [zone] is read on every use rather
 * than captured once, so a zone change takes effect on the next emission.
 */
class PeriodClock(
    private val clock: Clock,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    changes: Flow<Unit>,
) {
    fun now(): Instant = clock.instant()

    fun zone(): ZoneId = zone.invoke()

    fun currentYearMonth(): YearMonth = YearMonth.from(clock.instant().atZone(zone()))

    /** [1st 00:00, next 1st 00:00) of [month], in the current zone. */
    fun periodOf(month: YearMonth): Period {
        val zone = zone()
        return Period(
            start = month.atDay(1).atStartOfDay(zone).toInstant(),
            endExclusive = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(),
        )
    }

    fun calendarMonth(month: YearMonth): CalendarMonth = CalendarMonth(month, periodOf(month))

    /**
     * The current month, re-emitted when it changes. Compared by period, not just by month: a
     * time-zone change moves the boundaries even when the month stays the same.
     */
    val currentMonth: Flow<CalendarMonth> = changes
        .onStart { emit(Unit) }
        .map { calendarMonth(currentYearMonth()) }
        .distinctUntilChanged()
}
