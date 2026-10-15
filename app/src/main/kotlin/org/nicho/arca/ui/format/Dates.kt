package org.nicho.arca.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import org.nicho.arca.R

// Dates in the language the app is showing. java.time only: no device-locale surprises beyond
// the language itself.

@Composable
@ReadOnlyComposable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]

/** "Today", "Yesterday", "September 16" (this year) or "September 16, 2025". */
@Composable
fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> stringResource(R.string.date_today)
    today.minusDays(1) -> stringResource(R.string.date_yesterday)
    else -> date.format(
        DateTimeFormatter.ofPattern(if (date.year == today.year) "MMMM d" else "MMMM d, yyyy", appLocale()),
    )
}

/** "Sep 28". */
@Composable
fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("MMM d", appLocale()))

/** "Today, Sep 30" / "Yesterday, Sep 29" / "Sep 12" (the capture form's date row). */
@Composable
fun captureDate(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val short = shortDate(date)
    return when (date) {
        today -> stringResource(R.string.date_today) + ", " + short
        today.minusDays(1) -> stringResource(R.string.date_yesterday) + ", " + short
        else -> if (date.year == today.year) short else date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", appLocale()))
    }
}

/** "September 17, 2026 · 2:22 PM". */
@Composable
fun dateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val locale = appLocale()
    val at = instant.atZone(zone)
    return at.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", locale)) + " · " +
        at.format(DateTimeFormatter.ofPattern("h:mm a", locale))
}

/** "September" (this year) or "September 2025". */
@Composable
fun monthLabel(month: YearMonth, current: YearMonth = YearMonth.now()): String {
    val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, appLocale())
    return if (month.year == current.year) name else "$name ${month.year}"
}

/** "September 2026". */
@Composable
fun monthYearLabel(month: YearMonth): String =
    month.month.getDisplayName(TextStyle.FULL_STANDALONE, appLocale()) + " " + month.year
