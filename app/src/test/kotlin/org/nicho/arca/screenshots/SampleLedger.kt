package org.nicho.arca.screenshots

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.AccountType
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionId
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.seeding.DefaultCategoryProvider.BLUE
import org.nicho.arca.domain.seeding.DefaultCategoryProvider.CLAY
import org.nicho.arca.domain.seeding.DefaultCategoryProvider.OLIVE
import org.nicho.arca.domain.seeding.DefaultCategoryProvider.PLUM
import org.nicho.arca.domain.seeding.DefaultCategoryProvider.TEAL
import org.nicho.arca.testing.InMemoryLedger
import org.nicho.arca.testing.TestPeriodClock
import org.nicho.arca.testing.testAccount
import org.nicho.arca.testing.testCategory
import org.nicho.arca.testing.testTransfer

/**
 * The prototype's sample month, September 2026, seen on the 30th: the same accounts, categories
 * and roughly the same totals, so a rendered screen can be held against its board.
 */
class SampleLedger {
    val zone: ZoneId = ZoneId.of("Asia/Jakarta")
    val clock = TestPeriodClock(at(2026, 9, 30, 10, 0), zone)
    val ledger = InMemoryLedger()

    val bca = testAccount("BCA Utama", AccountType.BANK, openingBalanceMinor = 9_200_000, isFavorite = true, sortOrder = 0).copy(icon = "landmark")
    val jenius = testAccount("Jenius", AccountType.BANK, openingBalanceMinor = 3_700_000, sortOrder = 1).copy(icon = "landmark")
    val gopay = testAccount("GoPay", AccountType.EWALLET, openingBalanceMinor = 0, isFavorite = true, sortOrder = 2).copy(icon = "smartphone")
    val ovo = testAccount("OVO", AccountType.EWALLET, openingBalanceMinor = 200_000, sortOrder = 3).copy(icon = "smartphone")
    val shopee = testAccount("ShopeePay", AccountType.EWALLET, openingBalanceMinor = 0, sortOrder = 4).copy(icon = "smartphone")
    val wallet = testAccount("Wallet", AccountType.CASH, openingBalanceMinor = 150_000, sortOrder = 5).copy(icon = "wallet")
    val oldBank = testAccount("Mandiri (old)", AccountType.BANK, openingBalanceMinor = 0, isArchived = true, sortOrder = 6).copy(icon = "landmark")

    val food = category("Food & Drinks", "utensils", CLAY, budget = 2_500_000)
    val transport = category("Transport", "car", BLUE)
    val shopping = category("Shopping", "shopping-bag", TEAL, budget = 1_000_000)
    val bills = category("Bills", "file-text", PLUM, budget = 570_000)
    val health = category("Health", "heart", OLIVE)
    val fun_ = category("Entertainment", "film", CLAY)
    val others = category("Others", "ellipsis", BLUE)
    val fees = testCategory("Fees", isSystemLocked = true).copy(icon = "banknote", colorHex = TEAL)
    val salary = testCategory("Salary", kind = CategoryKind.INCOME).copy(icon = "briefcase", colorHex = PLUM)

    val month: YearMonth = YearMonth.of(2026, 9)

    suspend fun build(): SampleLedger {
        listOf(bca, jenius, gopay, ovo, shopee, wallet, oldBank).forEach { ledger.accounts.insert(it) }
        listOf(food, transport, shopping, bills, health, fun_, others, salary).forEach { ledger.categories.insert(it.copy(budgetMinor = null)) }
        for (c in listOf(food, shopping, bills)) ledger.budgets.setBudget(c.id, YearMonth.of(2026, 8), c.budgetMinor)

        val tx = ledger.transactions
        // September
        tx.insert(row(TransactionType.INCOME, bca, 8_000_000, at(2026, 9, 25, 9, 0), salary, "September salary"))
        tx.insert(row(TransactionType.INCOME, gopay, 200_000, at(2026, 9, 12, 19, 0), salary, "Cashback"))
        tx.insertAtomic(testTransfer(bca, gopay, 200_000, at(2026, 9, 30, 8, 30)))
        expenses(2026, 9, food, listOf(32_000 to "Lunch at the warung", 1_100_000 to "Groceries", 418_000 to null, 700_000 to "Dinner"), gopay)
        expenses(2026, 9, transport, listOf(1_000_000 to "Fuel", 280_000 to "Gojek"), bca)
        expenses(2026, 9, shopping, listOf(965_000 to "Shoes"), bca)
        expenses(2026, 9, bills, listOf(610_000 to "Electricity"), bca)
        expenses(2026, 9, others, listOf(245_000 to null), wallet)
        expenses(2026, 9, food, listOf(120_000 to "Snacks"), shopee)
        // August, for the comparisons
        tx.insert(row(TransactionType.INCOME, bca, 8_000_000, at(2026, 8, 25, 9, 0), salary, "August salary"))
        expenses(2026, 8, food, listOf(2_480_000 to null), gopay)
        expenses(2026, 8, transport, listOf(1_460_000 to null), bca)
        expenses(2026, 8, shopping, listOf(1_120_000 to null), bca)
        expenses(2026, 8, bills, listOf(680_000 to null), bca)
        expenses(2026, 8, others, listOf(380_000 to null), wallet)
        return this
    }

    private var day = 0

    private suspend fun expenses(year: Int, month: Int, category: Category, amounts: List<Pair<Int, String?>>, account: Account) {
        for ((amount, note) in amounts) {
            day = (day % 28) + 1
            val dayOfMonth = if (year == 2026 && month == 9) minOf(day, 29) else day
            ledger.transactions.insert(row(TransactionType.EXPENSE, account, amount.toLong(), at(year, month, dayOfMonth, 12, 15), category, note))
        }
    }

    private fun category(name: String, icon: String, color: String, budget: Long? = null) =
        testCategory(name, budgetMinor = budget).copy(icon = icon, colorHex = color)

    private fun row(type: TransactionType, account: Account, amount: Long, date: Instant, category: Category?, note: String?) =
        Transaction(TransactionId.random(), amount, type, date, note, account.id, category?.id, null, null)

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Instant =
        LocalDate.of(y, m, d).atTime(LocalTime.of(h, min)).atZone(ZoneId.of("Asia/Jakarta")).toInstant()
}
