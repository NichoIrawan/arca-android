package org.nicho.arca.domain.model

// FR-301. Transfers excluded (DD-10). netMinor = income − expense and may be negative.
data class PeriodSummary(
    val incomeMinor: Long,
    val expenseMinor: Long,
    val netMinor: Long,
) {
    companion object {
        val EMPTY = PeriodSummary(incomeMinor = 0, expenseMinor = 0, netMinor = 0)
    }
}
