package org.nicho.arca.data

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nicho.arca.data.seeding.DemoDataGenerator
import org.nicho.arca.domain.model.AccountType
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.seeding.DemoDataConfig

// Plain JVM: the generator is pure, so its rules are checked without a database.
class DemoDataGeneratorTest {

    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val zone = ZoneId.of("Asia/Jakarta")
    private val config = DemoDataConfig(months = 3, accounts = 20)
    private val demo = DemoDataGenerator(config, now, zone).generate()

    private val categoriesById = demo.categories.associateBy { it.id }
    private val rows = demo.transactionGroups.flatten()

    @Test
    fun sameInputs_giveIdenticalData_idsIncluded() {
        val again = DemoDataGenerator(config, now, zone).generate()

        assertEquals(demo.accounts, again.accounts)
        assertEquals(demo.categories, again.categories)
        assertEquals(demo.allocations, again.allocations)
        assertEquals(demo.transactionGroups, again.transactionGroups)
    }

    @Test
    fun accounts_coverAllFourTypes_withThreeFavourites() {
        assertEquals(20, demo.accounts.size)
        assertEquals(AccountType.entries.map { it.code }.toSet(), demo.accounts.map { it.type }.toSet())
        assertEquals(listOf("BCA", "GoPay", "Dompet"), demo.accounts.filter { it.isFavorite }.map { it.name })
    }

    @Test
    fun categories_areAbout25_withOneLockedFees() {
        assertEquals(25, demo.categories.size)
        assertEquals(1, demo.categories.count { it.isSystemLocked })
        assertEquals(demo.categories.size, demo.categories.map { it.name }.toSet().size)
    }

    @Test
    fun everyTransferGroup_isOutInAndAnOptionalFee() {
        val groups = rows.filter { it.transferGroupId != null }.groupBy { it.transferGroupId }
        assertTrue(groups.isNotEmpty())
        assertTrue(groups.values.any { it.size == 3 }) // some top-ups carry a fee

        groups.values.forEach { members ->
            val out = members.single { it.type == TransactionType.TRANSFER_OUT.code }
            val into = members.single { it.type == TransactionType.TRANSFER_IN.code }
            assertEquals(out.amountMinor, into.amountMinor)
            assertEquals(into.accountId, out.counterAccountId)
            assertEquals(out.accountId, into.counterAccountId)
            members.filter { it.type == TransactionType.EXPENSE.code }.forEach { fee ->
                assertTrue(categoriesById.getValue(fee.categoryId!!).isSystemLocked)
                assertEquals(out.accountId, fee.accountId)
                assertEquals(null, fee.counterAccountId)
            }
            assertTrue(members.size in 2..3)
        }
    }

    @Test
    fun everyRow_satisfiesTheDataModelRules() {
        rows.forEach { row ->
            assertTrue("amount > 0", row.amountMinor > 0)
            assertTrue("not in the future", row.date <= now.toEpochMilli())
            when (row.type) {
                TransactionType.INCOME.code ->
                    assertEquals(CategoryKind.INCOME.code, categoriesById.getValue(row.categoryId!!).kind)
                TransactionType.EXPENSE.code ->
                    assertEquals(CategoryKind.EXPENSE.code, categoriesById.getValue(row.categoryId!!).kind)
                else -> assertEquals(null, row.categoryId) // transfer principals have no category
            }
        }
    }

    @Test
    fun rowsPerDay_averageNearTheTarget() {
        val days = rows.map { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }.distinct().size
        val perDay = rows.size.toDouble() / days
        assertTrue("got $perDay per day", perDay in 24.0..30.0)
    }

    @Test
    fun budgets_matchTheirLatestAllocation_andFeesIsNeverBudgeted() {
        val latest = demo.allocations.groupBy { it.categoryId }.mapValues { (_, a) -> a.maxBy { it.month }.amountMinor }
        demo.categories.forEach { c -> assertEquals(c.name, latest[c.id], c.budgetMinor) }
        assertTrue(demo.categories.single { it.isSystemLocked }.budgetMinor == null)
    }
}
