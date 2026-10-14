package org.nicho.arca.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nicho.arca.domain.calculation.ReportAggregator
import org.nicho.arca.domain.model.Granularity
import org.nicho.arca.domain.model.PeriodSummary
import org.nicho.arca.domain.model.TimeBucket
import org.nicho.arca.testing.testAccount
import org.nicho.arca.testing.testCategory
import org.nicho.arca.testing.testExpense
import org.nicho.arca.testing.testIncome
import org.nicho.arca.testing.testTransfer
import org.nicho.arca.domain.model.CategoryKind

// SDD-ARC-001 §12.2 ReportAggregatorTests: transfers are not income or spending (SRS §6.3).
class ReportAggregatorTest {

    private val bca = testAccount("BCA")
    private val gopay = testAccount("GoPay")
    private val food = testCategory("Makanan")
    private val transport = testCategory("Transportasi")
    private val fees = testCategory("Biaya", isSystemLocked = true)
    private val salary = testCategory("Gaji", kind = CategoryKind.INCOME)
    private val categories = listOf(food, transport, fees, salary).associateBy { it.id }
    private val october = monthPeriod(2026, 10)
    private val day = at(2026, 10, 5)

    @Test
    fun transfers_areNeitherIncomeNorExpense_butTheirFeeIsAnExpense() {
        val txs = testTransfer(bca, gopay, 500_000, day, fee = 2_500L to fees) +
            testIncome(bca, salary, 10_000_000, day) +
            testExpense(bca, food, 50_000, day)

        assertEquals(PeriodSummary(10_000_000, 52_500, 9_947_500), ReportAggregator.summary(txs, october))
    }

    @Test
    fun theFee_appearsUnderFees_andThePrincipalInNoCategory() {
        val txs = testTransfer(bca, gopay, 500_000, day, fee = 2_500L to fees)

        val spend = ReportAggregator.spendByCategory(txs, categories, october)

        assertEquals(listOf(fees.id to 2_500L), spend.map { it.categoryId to it.totalMinor })
    }

    @Test
    fun spendByCategory_isLargestFirst_withSharesThatSumToOne() {
        val txs = listOf(
            testExpense(bca, food, 30_000, day),
            testExpense(bca, transport, 70_000, day),
            testExpense(bca, food, 20_000, day),
        )

        val spend = ReportAggregator.spendByCategory(txs, categories, october)

        assertEquals(listOf(transport.id to 70_000L, food.id to 50_000L), spend.map { it.categoryId to it.totalMinor })
        assertEquals(1f, spend.map { it.share }.sum(), 0.0001f)
        assertEquals(food.icon, spend[1].icon) // NFR-A04: the icon travels with the colour
    }

    @Test
    fun periodStart_isIncluded_andPeriodEnd_isExcluded() {
        val atStart = testExpense(bca, food, 1_000, at(2026, 10, 1, hour = 0))
        val lastMoment = testExpense(bca, food, 2_000, at(2026, 10, 31, hour = 23, minute = 59, second = 59))
        val atEnd = testExpense(bca, food, 4_000, at(2026, 11, 1, hour = 0))

        assertEquals(3_000L, ReportAggregator.summary(listOf(atStart, lastMoment, atEnd), october).expenseMinor)
    }

    @Test
    fun anEmptyPeriod_isAZeroedSummary() {
        assertEquals(PeriodSummary.EMPTY, ReportAggregator.summary(emptyList(), october))
        assertTrue(ReportAggregator.spendByCategory(emptyList(), categories, october).isEmpty())
    }

    @Test
    fun incomeByCategory_countsIncomeOnly() {
        val txs = listOf(testIncome(bca, salary, 15_000_000, day), testExpense(bca, food, 10_000, day)) +
            testTransfer(bca, gopay, 100_000, day)

        val income = ReportAggregator.incomeByCategory(txs, categories, october)

        assertEquals(listOf(salary.id to 15_000_000L), income.map { it.categoryId to it.totalMinor })
    }

    @Test
    fun spendOverTime_hasOneBucketPerDay_includingEmptyDays_andIgnoresTransfers() {
        val txs = listOf(
            testExpense(bca, food, 10_000, at(2026, 10, 1, hour = 0, minute = 30)), // just after local midnight
            testExpense(bca, food, 5_000, at(2026, 10, 3)),
        ) + testTransfer(bca, gopay, 999_000, at(2026, 10, 3))

        val buckets = ReportAggregator.spendOverTime(txs, october, Granularity.DAY, JAKARTA)

        assertEquals(31, buckets.size)
        assertEquals(TimeBucket(LocalDate.of(2026, 10, 1), 10_000), buckets[0])
        assertEquals(TimeBucket(LocalDate.of(2026, 10, 2), 0), buckets[1])
        assertEquals(TimeBucket(LocalDate.of(2026, 10, 3), 5_000), buckets[2])
    }

    @Test
    fun spendOverTime_monthly_acrossAYear() {
        val year = monthPeriod(2026, 1).copy(endExclusive = monthPeriod(2027, 1).start)
        val txs = listOf(testExpense(bca, food, 7_000, at(2026, 3, 15)))

        val buckets = ReportAggregator.spendOverTime(txs, year, Granularity.MONTH, JAKARTA)

        assertEquals(12, buckets.size)
        assertEquals(7_000L, buckets.single { it.start == LocalDate.of(2026, 3, 1) }.totalMinor)
    }
}
