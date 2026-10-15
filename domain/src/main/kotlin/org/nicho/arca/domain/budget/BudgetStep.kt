package org.nicho.arca.domain.budget

/**
 * The category editor's − / + stepper (board 26): a budget moves in Rp50,000 steps, never below
 * one step or above Rp100,000,000. Lives here because stepping is money arithmetic (DD-21).
 */
object BudgetStep {
    const val STEP_MINOR: Long = 50_000
    const val MAX_MINOR: Long = 100_000_000

    /** What "Set a budget" starts from. */
    const val DEFAULT_MINOR: Long = 500_000

    fun up(currentMinor: Long?): Long = minOf(MAX_MINOR, Math.addExact(currentMinor ?: 0L, STEP_MINOR))

    fun down(currentMinor: Long?): Long = maxOf(STEP_MINOR, Math.subtractExact(currentMinor ?: STEP_MINOR, STEP_MINOR))

    fun canGoDown(currentMinor: Long?): Boolean = (currentMinor ?: 0L) > STEP_MINOR
}
