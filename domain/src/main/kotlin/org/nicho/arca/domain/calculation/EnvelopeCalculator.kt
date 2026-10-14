package org.nicho.arca.domain.calculation

import java.time.YearMonth
import org.nicho.arca.domain.model.BudgetAllocation
import org.nicho.arca.domain.model.BudgetStatus
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.model.CategorySpend
import org.nicho.arca.domain.model.Envelope
import org.nicho.arca.domain.model.EnvelopeSummary

/**
 * SDD-ARC-002 §7.9, DD-A19 · FR-260–266. The third and last home of money arithmetic. It never
 * computes spend itself: per-month spend comes from ReportAggregator.spendByCategory, so the
 * "transfers aren't spending" filter is applied in exactly one place.
 *
 * The fold, per category, from m0 = the month of its first allocation:
 *
 *     budget(m)    = amount of the latest allocation with month ≤ m   (none or null ⇒ not budgeted in m)
 *     carry(m0)    = 0
 *     available(m) = budget(m) + carry(m)
 *     left(m)      = available(m) − spent(m)                           (negative ⇒ OVER_BUDGET)
 *     carry(m + 1) = rollsOver && budget(m) != null ? max(0, left(m)) : 0
 *
 * - max(0, …): overspending never carries, so one bad month can't compound (FR-262, BEH-P2).
 * - No term reads another envelope, so one envelope never covers another.
 * - A month without a budget breaks the chain.
 * - rollsOver is read as it is *now*, for every month (OPEN-A14).
 *
 * Carry is recomputed on every read and never stored (DD-34), so a backdated expense corrects
 * every later month with nothing to repair.
 */
object EnvelopeCalculator {

    /**
     * Evaluates [month].
     *
     * Envelopes: expense categories that aren't system-locked or archived and have a budget in
     * force in [month]. Every other expense category (Fees included) is "not budgeted" (FR-266),
     * except archived ones with no spend this month. Income categories are neither.
     *
     * [spendByMonth] needs one entry per month from the earliest allocation through [month]
     * (missing months count as no spend).
     */
    fun evaluate(
        month: YearMonth,
        categories: List<Category>,
        allocations: List<BudgetAllocation>,
        spendByMonth: Map<YearMonth, List<CategorySpend>>,
    ): EnvelopeSummary {
        val allocationsByCategory = allocations.groupBy { it.categoryId }
        val spendThisMonth = spendByMonth[month].orEmpty().associateBy { it.categoryId }

        val envelopes = mutableListOf<Envelope>()
        val notBudgeted = mutableListOf<CategorySpend>()
        for (category in categories) {
            if (category.kind != CategoryKind.EXPENSE) continue
            val envelope = if (category.isSystemLocked || category.isArchived) {
                null
            } else {
                envelopeFor(category, allocationsByCategory[category.id].orEmpty(), month, spendByMonth)
            }
            when {
                envelope != null -> envelopes += envelope
                category.isArchived && category.id !in spendThisMonth -> Unit
                else -> notBudgeted += spendThisMonth[category.id] ?: noSpend(category)
            }
        }

        var available = 0L
        var spentInEnvelopes = 0L
        for (e in envelopes) {
            available = Math.addExact(available, e.availableMinor)
            spentInEnvelopes = Math.addExact(spentInEnvelopes, e.spentMinor)
        }
        var allSpending = spentInEnvelopes
        for (s in notBudgeted) allSpending = Math.addExact(allSpending, s.totalMinor)

        return EnvelopeSummary(
            month = month,
            envelopes = envelopes,
            notBudgeted = notBudgeted.sortedWith(compareByDescending<CategorySpend> { it.totalMinor }.thenBy { it.name }),
            totalAvailableMinor = available,
            totalSpentInEnvelopesMinor = spentInEnvelopes,
            totalLeftMinor = Math.subtractExact(available, spentInEnvelopes),
            totalStatus = status(if (envelopes.isEmpty()) null else available, spentInEnvelopes),
            allSpendingMinor = allSpending,
        )
    }

    /**
     * FR-953, the single threshold rule: no budget → NO_BUDGET_SET; spent > available →
     * OVER_BUDGET; spent ≥ 90 % of available → NEAR_LIMIT; otherwise UNDER_BUDGET.
     * Integer comparison (spent × 10 ≥ available × 9), no percentage float.
     */
    fun status(availableMinor: Long?, spentMinor: Long): BudgetStatus = when {
        availableMinor == null -> BudgetStatus.NO_BUDGET_SET
        spentMinor > availableMinor -> BudgetStatus.OVER_BUDGET
        Math.multiplyExact(spentMinor, 10L) >= Math.multiplyExact(availableMinor, 9L) -> BudgetStatus.NEAR_LIMIT
        else -> BudgetStatus.UNDER_BUDGET
    }

    /** The fold for one category, or null if no budget is in force in [month]. */
    private fun envelopeFor(
        category: Category,
        allocations: List<BudgetAllocation>,
        month: YearMonth,
        spendByMonth: Map<YearMonth, List<CategorySpend>>,
    ): Envelope? {
        if (allocations.isEmpty()) return null
        val changes = allocations.sortedBy { it.month }
        val firstMonth = changes.first().month
        if (month < firstMonth) return null

        var carry = 0L
        var m = firstMonth
        while (true) {
            val budget = changes.lastOrNull { it.month <= m }?.amountMinor
            val spent = spentIn(spendByMonth, m, category.id)
            if (m == month) {
                if (budget == null) return null
                val available = Math.addExact(budget, carry)
                val left = Math.subtractExact(available, spent)
                return Envelope(
                    categoryId = category.id,
                    budgetMinor = budget,
                    carryMinor = carry,
                    availableMinor = available,
                    spentMinor = spent,
                    leftMinor = left,
                    rollsOver = category.rollsOver,
                    status = status(available, spent),
                )
            }
            carry = if (category.rollsOver && budget != null) {
                maxOf(0L, Math.subtractExact(Math.addExact(budget, carry), spent))
            } else {
                0L
            }
            m = m.plusMonths(1)
        }
    }

    private fun spentIn(spendByMonth: Map<YearMonth, List<CategorySpend>>, month: YearMonth, id: CategoryId): Long =
        spendByMonth[month]?.firstOrNull { it.categoryId == id }?.totalMinor ?: 0L

    private fun noSpend(category: Category) = CategorySpend(
        categoryId = category.id,
        name = category.name,
        icon = category.icon,
        colorHex = category.colorHex,
        totalMinor = 0L,
        share = 0f,
    )
}
