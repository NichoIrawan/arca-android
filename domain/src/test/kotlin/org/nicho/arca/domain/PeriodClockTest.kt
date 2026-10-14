package org.nicho.arca.domain

import app.cash.turbine.test
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.nicho.arca.testing.TestPeriodClock

// SDD-ARC-002 §14.2 "Period clock": a time-zone change and a date change each re-emit a
// correct month (DD-A18).
class PeriodClockTest {

    private val test = TestPeriodClock(NOW, JAKARTA)

    @Test
    fun periodOf_isTheLocalCalendarMonth_halfOpen() {
        val period = test.periodClock.periodOf(ym(2026, 10))
        assertEquals(at(2026, 10, 1, hour = 0), period.start)
        assertEquals(at(2026, 11, 1, hour = 0), period.endExclusive)
    }

    @Test
    fun crossingIntoANewMonth_reEmits() = runTest {
        test.periodClock.currentMonth.test {
            assertEquals(ym(2026, 10), awaitItem().month)

            test.now = at(2026, 11, 1, hour = 0, minute = 1)
            test.notifyChanged()

            assertEquals(ym(2026, 11), awaitItem().month)
        }
    }

    @Test
    fun aTimeZoneChange_reEmits_evenInTheSameMonth() = runTest {
        test.periodClock.currentMonth.test {
            val jakarta = awaitItem()

            test.zone = ZoneId.of("Asia/Tokyo")
            test.notifyChanged()

            val tokyo = awaitItem()
            assertEquals(jakarta.month, tokyo.month)
            assertEquals(jakarta.period.start.minusSeconds(2 * 3600), tokyo.period.start) // 2 hours earlier
        }
    }

    @Test
    fun aSignalWithNothingChanged_emitsNothing() = runTest {
        test.periodClock.currentMonth.test {
            awaitItem()
            test.now = NOW.plusSeconds(3600) // same month, same zone
            test.notifyChanged()
            expectNoEvents()
        }
    }
}
