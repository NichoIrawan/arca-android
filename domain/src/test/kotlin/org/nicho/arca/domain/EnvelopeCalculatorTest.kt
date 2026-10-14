package org.nicho.arca.domain

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nicho.arca.domain.calculation.EnvelopeCalculator
import org.nicho.arca.domain.model.BudgetAllocation
import org.nicho.arca.domain.model.BudgetStatus
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.model.CategorySpend
import org.nicho.arca.domain.model.Envelope
import org.nicho.arca.domain.model.EnvelopeSummary
import org.nicho.arca.testing.testCategory

// SDD-ARC-002 §7.9 table · FR-260–265. Cases 1 and 2 are the SRS worked example and must match
// exactly.
class EnvelopeCalculatorTest {

    private val jul = ym(2026, 7)
    private val aug = ym(2026, 8)
    private val sep = ym(2026, 9)
    private val oct = ym(2026, 10)

    private val transport = testCategory("Transportasi", rollsOver = true)
    private val bills = testCategory("Tagihan", rollsOver = true)

    /** Spend for one category, by month. */
    private fun spend(category: Category, vararg months: Pair<YearMonth, Long>): Map<YearMonth, List<CategorySpend>> =
        months.associate { (m, total) ->
            m to listOf(CategorySpend(category.id, category.name, category.icon, category.colorHex, total, 1f))
        }

    private fun merge(vararg maps: Map<YearMonth, List<CategorySpend>>) =
        maps.flatMap { it.entries }.groupBy({ it.key }, { it.value }).mapValues { (_, lists) -> lists.flatten() }

    private fun evaluate(
        month: YearMonth,
        categories: List<Category>,
        allocations: List<BudgetAllocation>,
        spend: Map<YearMonth, List<CategorySpend>>,
    ): EnvelopeSummary = EnvelopeCalculator.evaluate(month, categories, allocations, spend)

    private fun EnvelopeSummary.of(category: Category): Envelope? = envelopes.firstOrNull { it.categoryId == category.id }

    // ── Case 1: Transport, roll-over on ──
    private val transportAllocations = listOf(BudgetAllocation(transport.id, jul, 1_300_000))
    private val transportSpend = spend(transport, jul to 1_040_000, aug to 1_460_000, sep to 1_280_000)

    @Test
    fun case1_transport_rollsOverTheUnspentAmount() {
        val augEnvelope = evaluate(aug, listOf(transport), transportAllocations, transportSpend).of(transport)!!
        assertEquals(260_000L, augEnvelope.carryMinor)
        assertEquals(1_560_000L, augEnvelope.availableMinor)

        val sepEnvelope = evaluate(sep, listOf(transport), transportAllocations, transportSpend).of(transport)!!
        assertEquals(100_000L, sepEnvelope.carryMinor)
        assertEquals(1_400_000L, sepEnvelope.availableMinor)
        assertEquals(120_000L, sepEnvelope.leftMinor)
        // 1,280,000 of 1,400,000 is 91 %: past the 90 % line. (SDD-ARC-001 §12.2 lists this row as
        // underBudget, which contradicts its own FR-953 threshold.)
        assertEquals(BudgetStatus.NEAR_LIMIT, sepEnvelope.status)

        val octEnvelope = evaluate(oct, listOf(transport), transportAllocations, transportSpend).of(transport)!!
        assertEquals(120_000L, octEnvelope.carryMinor)
        assertEquals(1_420_000L, octEnvelope.availableMinor)
    }

    // ── Case 2: Bills, overspent ──
    private val billsAllocations = listOf(BudgetAllocation(bills.id, sep, 570_000))
    private val billsSpend = spend(bills, sep to 610_000)

    @Test
    fun case2_overspending_isOverBudget_andNeverCarries() {
        val sepEnvelope = evaluate(sep, listOf(bills), billsAllocations, billsSpend).of(bills)!!
        assertEquals(-40_000L, sepEnvelope.leftMinor)
        assertEquals(BudgetStatus.OVER_BUDGET, sepEnvelope.status)

        val octEnvelope = evaluate(oct, listOf(bills), billsAllocations, billsSpend).of(bills)!!
        assertEquals(0L, octEnvelope.carryMinor)
        assertEquals(570_000L, octEnvelope.availableMinor)
    }

    @Test
    fun case3_rollOverOff_resetsEachMonth() {
        val c = testCategory(rollsOver = false)
        val sepEnvelope = evaluate(sep, listOf(c), listOf(BudgetAllocation(c.id, aug, 500_000)), spend(c, aug to 200_000)).of(c)!!
        assertEquals(0L, sepEnvelope.carryMinor)
        assertEquals(500_000L, sepEnvelope.availableMinor)
    }

    @Test
    fun case4_aBudgetChange_appliesFromItsMonthForward() {
        val c = testCategory()
        val allocations = listOf(BudgetAllocation(c.id, jul, 1_000_000), BudgetAllocation(c.id, sep, 800_000))
        assertEquals(1_000_000L, evaluate(jul, listOf(c), allocations, emptyMap()).of(c)!!.budgetMinor)
        assertEquals(1_000_000L, evaluate(aug, listOf(c), allocations, emptyMap()).of(c)!!.budgetMinor)
        assertEquals(800_000L, evaluate(sep, listOf(c), allocations, emptyMap()).of(c)!!.budgetMinor)
    }

    @Test
    fun case5_aBackdatedExpense_correctsEveryLaterCarry() {
        // August's total now includes the backdated 50,000.
        val withBackdate = spend(transport, jul to 1_040_000, aug to 1_510_000, sep to 1_280_000)

        val sepEnvelope = evaluate(sep, listOf(transport), transportAllocations, withBackdate).of(transport)!!
        assertEquals(50_000L, sepEnvelope.carryMinor)
        assertEquals(1_350_000L, sepEnvelope.availableMinor)
        assertEquals(70_000L, sepEnvelope.leftMinor)
        assertEquals(70_000L, evaluate(oct, listOf(transport), transportAllocations, withBackdate).of(transport)!!.carryMinor)
    }

    @Test
    fun case6_aRemovedBudget_isNotBudgeted_andBreaksTheCarryChain() {
        val c = testCategory(rollsOver = true)
        val allocations = listOf(
            BudgetAllocation(c.id, aug, 500_000),
            BudgetAllocation(c.id, sep, null),
            BudgetAllocation(c.id, oct, 500_000),
        )
        val spending = spend(c, aug to 200_000, sep to 30_000)

        val sepSummary = evaluate(sep, listOf(c), allocations, spending)
        assertNull(sepSummary.of(c))
        assertEquals(30_000L, sepSummary.notBudgeted.single().totalMinor)

        val octEnvelope = evaluate(oct, listOf(c), allocations, spending).of(c)!!
        assertEquals(0L, octEnvelope.carryMinor) // Aug's 300,000 surplus doesn't come back
    }

    @Test
    fun case7_fees_isNeverAnEnvelope() {
        val fees = testCategory("Biaya", isSystemLocked = true)
        val summary = evaluate(oct, listOf(fees), listOf(BudgetAllocation(fees.id, oct, 50_000)), spend(fees, oct to 7_500))
        assertTrue(summary.envelopes.isEmpty())
        assertEquals(7_500L, summary.notBudgeted.single().totalMinor)
    }

    // FR-953 thresholds, with available 1,000,000.
    @Test
    fun case8_statusThresholds() {
        assertEquals(BudgetStatus.UNDER_BUDGET, EnvelopeCalculator.status(1_000_000, 899_999))
        assertEquals(BudgetStatus.NEAR_LIMIT, EnvelopeCalculator.status(1_000_000, 900_000))
        assertEquals(BudgetStatus.NEAR_LIMIT, EnvelopeCalculator.status(1_000_000, 1_000_000))
        assertEquals(BudgetStatus.OVER_BUDGET, EnvelopeCalculator.status(1_000_000, 1_000_001))
        assertEquals(BudgetStatus.NO_BUDGET_SET, EnvelopeCalculator.status(null, 5_000))
    }

    @Test
    fun case9_totals_coverEnvelopesOnly() {
        val food = testCategory("Makanan")
        val summary = evaluate(
            sep,
            listOf(transport, bills, food),
            transportAllocations + billsAllocations,
            merge(transportSpend, billsSpend, spend(food, sep to 400_000)),
        )
        assertEquals(1_970_000L, summary.totalAvailableMinor)
        assertEquals(1_890_000L, summary.totalSpentInEnvelopesMinor)
        assertEquals(80_000L, summary.totalLeftMinor)
        assertEquals(BudgetStatus.NEAR_LIMIT, summary.totalStatus)
        assertEquals(2_290_000L, summary.allSpendingMinor) // unbudgeted Makanan still counts here
    }

    @Test
    fun case10_overflow_throws() {
        val c = testCategory(rollsOver = true)
        assertThrows(ArithmeticException::class.java) {
            evaluate(aug, listOf(c), listOf(BudgetAllocation(c.id, jul, Long.MAX_VALUE - 10)), emptyMap())
        }
    }

    @Test
    fun noEnvelopes_totalsAreNoBudgetSet() {
        val summary = evaluate(oct, listOf(testCategory()), emptyList(), emptyMap())
        assertEquals(BudgetStatus.NO_BUDGET_SET, summary.totalStatus)
        assertEquals(0L, summary.totalAvailableMinor)
    }

    @Test
    fun incomeAndQuietArchivedCategories_appearNowhere() {
        val salary = testCategory("Gaji", kind = CategoryKind.INCOME)
        val oldUnused = testCategory("Lama", isArchived = true)
        val oldUsed = testCategory("Lama 2", isArchived = true)

        val summary = evaluate(oct, listOf(salary, oldUnused, oldUsed), emptyList(), spend(oldUsed, oct to 1_000))

        assertTrue(summary.envelopes.isEmpty())
        assertEquals(listOf(oldUsed.id), summary.notBudgeted.map { it.categoryId })
    }
}
