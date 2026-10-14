package org.nicho.arca.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.nicho.arca.domain.model.Period

// Tests run in Jakarta time (UTC+7), so a month boundary is never the same instant as UTC
// midnight and a zone bug can't hide.
internal val JAKARTA: ZoneId = ZoneId.of("Asia/Jakarta")

/** An instant at a Jakarta wall-clock time. */
internal fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0, second: Int = 0): Instant =
    LocalDateTime.of(year, month, day, hour, minute, second).atZone(JAKARTA).toInstant()

internal fun ym(year: Int, month: Int): YearMonth = YearMonth.of(year, month)

/** A calendar month in Jakarta, half-open. */
internal fun monthPeriod(year: Int, month: Int): Period {
    val m = YearMonth.of(year, month)
    return Period(
        m.atDay(1).atStartOfDay(JAKARTA).toInstant(),
        m.plusMonths(1).atDay(1).atStartOfDay(JAKARTA).toInstant(),
    )
}

/** "Now" in every test: mid-October 2026. */
internal val NOW: Instant = at(2026, 10, 14)
