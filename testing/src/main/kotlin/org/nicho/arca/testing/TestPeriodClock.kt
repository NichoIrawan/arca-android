package org.nicho.arca.testing

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableSharedFlow
import org.nicho.arca.domain.time.PeriodClock

/**
 * A PeriodClock a test controls: move the time or the zone, then call [notifyChanged], which
 * stands in for the date/time-zone broadcasts :app forwards in production.
 */
class TestPeriodClock(now: Instant, zone: ZoneId = ZoneId.of("Asia/Jakarta")) {

    var now: Instant = now
    var zone: ZoneId = zone

    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    val clock: Clock = object : Clock() {
        override fun instant(): Instant = this@TestPeriodClock.now
        override fun getZone(): ZoneId = this@TestPeriodClock.zone
        override fun withZone(zone: ZoneId): Clock = this
    }

    val periodClock = PeriodClock(clock, zone = { this.zone }, changes = changes)

    fun notifyChanged() {
        changes.tryEmit(Unit)
    }
}
