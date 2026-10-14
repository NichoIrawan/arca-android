package org.nicho.arca.data.seeding

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random
import org.nicho.arca.data.db.entities.AccountEntity
import org.nicho.arca.data.db.entities.BudgetAllocationEntity
import org.nicho.arca.data.db.entities.CategoryEntity
import org.nicho.arca.data.db.entities.TransactionEntity
import org.nicho.arca.data.mapper.key
import org.nicho.arca.data.mapper.toEntity
import org.nicho.arca.domain.model.AccountType
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.seeding.DefaultCategoryProvider
import org.nicho.arca.domain.seeding.DemoDataConfig

/** Everything one seed writes. Each inner list is one write unit: a transfer's 2–3 rows stay together. */
internal class DemoData(
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val allocations: List<BudgetAllocationEntity>,
    val transactionGroups: List<List<TransactionEntity>>,
) {
    val transactionCount: Int get() = transactionGroups.sumOf { it.size } // rows, not money
}

/**
 * Builds the FR-902 demo data set (SDD-ARC-002 §8.5): weekday lunch and commute clusters, a
 * monthly salary, monthly bills, e-wallet top-ups as real transfers (some with a fee), and a
 * long tail of small QRIS payments that brings each day to about [DemoDataConfig.transactionsPerDay].
 *
 * Pure and deterministic: the same config, `now` and zone always give the same rows, ids
 * included, because every random choice comes from one seeded Random. That's what makes the
 * §14.3 performance runs comparable across builds.
 *
 * Writes entities directly instead of going through TransferService. That's the one exception
 * to DD-06, and the reason this lives in :data. The test checks the transfer shapes it produces.
 */
internal class DemoDataGenerator(
    private val config: DemoDataConfig,
    private val now: Instant,
    private val zone: ZoneId,
) {
    private val random = Random(config.randomSeed)
    private val nowMillis = now.toEpochMilli()
    private val today: LocalDate = now.atZone(zone).toLocalDate()
    private val firstMonth: YearMonth = YearMonth.from(today).minusMonths(config.months - 1L)
    private val createdAt: Long = firstMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    init {
        require(config.months >= 1) { "months must be ≥ 1" }
        require(config.accounts >= 3) { "accounts must be ≥ 3 (the three favourites)" }
        require(config.transactionsPerDay >= 1) { "transactionsPerDay must be ≥ 1" }
    }

    fun generate(): DemoData {
        val accounts = accounts()
        val categories = categories()
        val refs = Refs(accounts, categories)
        val allocations = allocations(refs)
        val budgeted = allocations.groupBy { it.categoryId }
            .mapValues { (_, rows) -> rows.maxBy { it.month }.amountMinor }
        val rollOver = setOf(refs.food, refs.transport)
        val finalCategories = categories.map { c ->
            if (c.id in budgeted) c.copy(budgetMinor = budgeted[c.id], rollsOver = c.id in rollOver) else c
        }

        val groups = mutableListOf<List<TransactionEntity>>()
        var date = firstMonth.atDay(1)
        while (!date.isAfter(today)) {
            groups += day(date, refs).filter { it.first().date <= nowMillis } // nothing in the future (FR-103)
            date = date.plusDays(1)
        }
        return DemoData(accounts, finalCategories, allocations, groups)
    }

    // ── Accounts ──

    private fun accounts(): List<AccountEntity> = (0 until config.accounts).map { i ->
        val (baseName, type) = ACCOUNT_TEMPLATES[i % ACCOUNT_TEMPLATES.size]
        val round = i / ACCOUNT_TEMPLATES.size
        AccountEntity(
            id = nextId(),
            name = if (round == 0) baseName else "$baseName ${round + 1}",
            type = type.code,
            openingBalanceMinor = when (type) {
                AccountType.BANK -> amount(2_000_000, 20_000_000, step = 100_000)
                AccountType.EWALLET -> amount(50_000, 500_000, step = 10_000)
                AccountType.CASH -> amount(100_000, 1_000_000, step = 10_000)
                AccountType.OTHER -> amount(0, 200_000, step = 10_000)
            },
            colorHex = DefaultCategoryProvider.palette[i % DefaultCategoryProvider.palette.size],
            icon = ACCOUNT_ICONS.getValue(type),
            isArchived = false,
            isFavorite = i < 3, // BCA, GoPay, Dompet
            sortOrder = i,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
    }

    // ── Categories: the ten defaults plus fifteen extras, about 25 in all ──

    private fun categories(): List<CategoryEntity> {
        val defaults = DefaultCategoryProvider.defaults()
            .map { it.copy(id = CategoryId(UUID.fromString(nextId()))).toEntity(createdAt, createdAt) }
        val extras = EXTRA_CATEGORIES.mapIndexed { i, (name, icon, kind) ->
            CategoryEntity(
                id = nextId(),
                name = name,
                icon = icon,
                colorHex = DefaultCategoryProvider.palette[(defaults.size + i) % DefaultCategoryProvider.palette.size],
                kind = kind.code,
                isArchived = false,
                isSystemLocked = false,
                budgetMinor = null,
                rollsOver = false,
                createdAt = createdAt,
                updatedAt = createdAt,
            )
        }
        return defaults + extras
    }

    // ── Budgets from the first month, so envelope folds span the whole range (§14.3) ──

    private fun allocations(refs: Refs): List<BudgetAllocationEntity> {
        val rows = mutableListOf(
            allocation(refs.food, firstMonth, 3_000_000),
            allocation(refs.transport, firstMonth, 1_300_000),
            allocation(refs.shopping, firstMonth, 1_500_000),
            allocation(refs.entertainment, firstMonth, 500_000),
            allocation(refs.bills, firstMonth, 1_000_000),
            allocation(refs.coffee, firstMonth, 600_000),
        )
        // A mid-range raise exercises "a change applies from its month forward" (FR-265).
        if (config.months >= 3) rows += allocation(refs.food, firstMonth.plusMonths(config.months / 2L), 3_500_000)
        return rows
    }

    private fun allocation(categoryId: String, month: YearMonth, amountMinor: Long) = BudgetAllocationEntity(
        id = nextId(),
        categoryId = categoryId,
        month = month.key(),
        amountMinor = amountMinor,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    // ── One day ──

    private fun day(date: LocalDate, r: Refs): List<List<TransactionEntity>> {
        val groups = mutableListOf<List<TransactionEntity>>()
        val dom = date.dayOfMonth
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

        // Monthly
        if (dom == 25) groups += income(r.bank, r.salary, amount(15_000_000, 20_000_000, step = 50_000), at(date, 9))
        if (dom == 15 && random.nextInt(3) == 0) {
            groups += income(r.secondBank, r.freelance, amount(1_000_000, 4_000_000, step = 50_000), at(date, 14))
        }
        if (dom == 3) {
            groups += expense(r.bank, r.bills, amount(300_000, 700_000, step = 1_000), at(date, 10), "Listrik")
            groups += expense(r.bank, r.bills, 350_000, at(date, 10), "Internet")
        }
        if (dom == 5) groups += expense(r.wallet, r.phoneData, 100_000, at(date, 20))
        if (dom == 7) {
            groups += expense(r.bank, r.subscriptions, 55_000, at(date, 6), "Spotify")
            groups += expense(r.bank, r.subscriptions, 120_000, at(date, 6), "Netflix")
        }
        if (dom == 28 && random.nextBoolean()) groups += expense(r.bank, r.donation, amount(50_000, 200_000, step = 10_000), at(date, 19))

        // Money moving between own accounts
        if (random.nextInt(100) < 45) {
            val fee = if (random.nextInt(100) < 40) FEES[random.nextInt(FEES.size)] else null
            groups += transfer(r.bank, r.pick(r.eWallets), amount(200_000, 1_500_000, step = 50_000), at(date, random.nextInt(7, 22)), fee, r)
        }
        if (date.dayOfWeek == DayOfWeek.FRIDAY && random.nextBoolean()) {
            groups += transfer(r.bank, r.cash, amount(200_000, 1_000_000, step = 50_000), at(date, 18), fee = null, r)
        }

        // Daily rhythm
        if (!weekend) {
            groups += expense(r.pick(r.everydayPayers), r.food, amount(25_000, 75_000), at(date, random.nextInt(11, 14)))
            if (random.nextInt(100) < 60) groups += expense(r.wallet, r.coffee, amount(18_000, 55_000), at(date, 8))
            groups += expense(r.wallet, r.transport, amount(12_000, 45_000), at(date, random.nextInt(7, 9)))
            groups += expense(r.wallet, r.transport, amount(12_000, 45_000), at(date, random.nextInt(17, 20)))
        } else {
            if (random.nextBoolean()) groups += expense(r.pick(listOf(r.bank, r.wallet)), r.shopping, amount(50_000, 500_000, step = 1_000), at(date, random.nextInt(10, 20)))
            if (random.nextInt(100) < 30) groups += expense(r.bank, r.entertainment, amount(50_000, 250_000, step = 1_000), at(date, random.nextInt(14, 22)))
            groups += expense(r.pick(r.everydayPayers), r.food, amount(60_000, 250_000, step = 1_000), at(date, random.nextInt(18, 21)))
        }

        // Occasional
        if (random.nextInt(100) < 3) groups += expense(r.bank, r.health, amount(50_000, 400_000, step = 5_000), at(date, random.nextInt(9, 20)))
        if (random.nextInt(100) < 15) groups += expense(r.pick(listOf(r.bank, r.wallet)), r.fuel, amount(50_000, 150_000, step = 5_000), at(date, random.nextInt(7, 21)))
        if (random.nextInt(100) < 20) groups += expense(r.cash, r.parking, amount(2_000, 10_000, step = 1_000), at(date, random.nextInt(8, 21)))
        if (random.nextInt(200) == 0) groups += expense(r.bank, r.gifts, amount(100_000, 500_000, step = 10_000), at(date, random.nextInt(10, 20)))

        // Long tail of small QRIS payments up to roughly the day's target
        var rows = groups.sumOf { it.size }
        val target = config.transactionsPerDay + random.nextInt(-3, 4)
        while (rows < target) {
            groups += expense(r.pick(r.smallPayers), r.pick(r.smallCategories), amount(2_000, 25_000), at(date, random.nextInt(7, 22)))
            rows++
        }
        return groups
    }

    // ── Row builders ──

    private fun expense(account: String, category: String, amountMinor: Long, date: Long, note: String? = null) =
        listOf(row(TransactionType.EXPENSE, account, amountMinor, date, note = note, categoryId = category))

    private fun income(account: String, category: String, amountMinor: Long, date: Long) =
        listOf(row(TransactionType.INCOME, account, amountMinor, date, categoryId = category))

    // FR-130–133: out at the source, in at the destination, optional fee as an ordinary expense
    // in Fees at the source. One group id on all of them; the fee has no counter account.
    private fun transfer(from: String, to: String, amountMinor: Long, date: Long, fee: Long?, r: Refs): List<TransactionEntity> {
        val group = nextId()
        return listOfNotNull(
            row(TransactionType.TRANSFER_OUT, from, amountMinor, date, groupId = group, counterAccountId = to),
            row(TransactionType.TRANSFER_IN, to, amountMinor, date, groupId = group, counterAccountId = from),
            fee?.let { row(TransactionType.EXPENSE, from, it, date, categoryId = r.fees, groupId = group) },
        )
    }

    private fun row(
        type: TransactionType,
        accountId: String,
        amountMinor: Long,
        date: Long,
        note: String? = null,
        categoryId: String? = null,
        groupId: String? = null,
        counterAccountId: String? = null,
    ) = TransactionEntity(
        id = nextId(),
        amountMinor = amountMinor,
        type = type.code,
        date = date,
        note = note,
        accountId = accountId,
        categoryId = categoryId,
        transferGroupId = groupId,
        counterAccountId = counterAccountId,
        createdAt = date,
        updatedAt = date,
    )

    // ── Randomness, all from the one seeded Random ──

    private fun at(date: LocalDate, hour: Int): Long =
        date.atTime(hour, random.nextInt(60)).atZone(zone).toInstant().toEpochMilli()

    /** A whole-rupiah amount in [min, max], rounded down to [step], never below one step. */
    private fun amount(min: Long, max: Long, step: Long = 500): Long =
        (random.nextLong(min, max + 1) / step).coerceAtLeast(1) * step

    /** A valid version-4 UUID string, but drawn from the seeded Random so ids repeat run to run. */
    private fun nextId(): String {
        val msb = (random.nextLong() and -0xf001L) or 0x4000L            // version 4
        val lsb = (random.nextLong() and 0x3fffffffffffffffL) or Long.MIN_VALUE // IETF variant
        return UUID(msb, lsb).toString()
    }

    /** Ids of the accounts and categories the day schedule refers to. */
    private inner class Refs(private val accountRows: List<AccountEntity>, categories: List<CategoryEntity>) {
        private val byName = categories.associate { it.name to it.id }
        private fun category(name: String) = byName.getValue(name)
        private fun ofType(type: AccountType) = accountRows.filter { it.type == type.code }.map { it.id }

        val bank = accountRows[0].id      // BCA: salary in, top-ups out
        val wallet = accountRows[1].id    // GoPay
        val cash = accountRows[2].id      // Dompet
        val secondBank = ofType(AccountType.BANK).getOrElse(1) { bank }
        val eWallets = ofType(AccountType.EWALLET)
        val everydayPayers = eWallets + cash
        val smallPayers = listOf(wallet, wallet, cash) + eWallets + ofType(AccountType.OTHER)

        val food = category("Makanan & Minuman")
        val transport = category("Transportasi")
        val shopping = category("Belanja")
        val bills = category("Tagihan")
        val health = category("Kesehatan")
        val entertainment = category("Hiburan")
        val other = category("Lainnya")
        val fees = categories.single { it.isSystemLocked }.id
        val salary = category("Gaji")
        val coffee = category("Kopi")
        val snacks = category("Jajan")
        val parking = category("Parkir")
        val fuel = category("Bensin")
        val phoneData = category("Pulsa & Data")
        val subscriptions = category("Langganan")
        val gifts = category("Hadiah")
        val donation = category("Donasi")
        val freelance = category("Freelance")
        val smallCategories = listOf(snacks, snacks, food, coffee, other)

        fun pick(ids: List<String>): String = ids[random.nextInt(ids.size)]
    }

    private companion object {
        val FEES = listOf(1_000L, 2_500L)

        // The first four cover all four types, so even a 5-account run has each type.
        val ACCOUNT_TEMPLATES = listOf(
            "BCA" to AccountType.BANK,
            "GoPay" to AccountType.EWALLET,
            "Dompet" to AccountType.CASH,
            "Mandiri" to AccountType.BANK,
            "Kartu Flazz" to AccountType.OTHER,
            "OVO" to AccountType.EWALLET,
            "BNI" to AccountType.BANK,
            "DANA" to AccountType.EWALLET,
            "Celengan" to AccountType.CASH,
            "ShopeePay" to AccountType.EWALLET,
            "Jago" to AccountType.BANK,
            "e-Money" to AccountType.OTHER,
            "BRI" to AccountType.BANK,
            "LinkAja" to AccountType.EWALLET,
            "SeaBank" to AccountType.BANK,
            "Jenius" to AccountType.BANK,
            "Brizzi" to AccountType.OTHER,
            "blu" to AccountType.BANK,
            "Amplop Darurat" to AccountType.CASH,
            "Permata" to AccountType.BANK,
        )

        // DSG-ARC-001 §7.1 account-type icons.
        val ACCOUNT_ICONS = mapOf(
            AccountType.CASH to "wallet",
            AccountType.BANK to "landmark",
            AccountType.EWALLET to "smartphone",
            AccountType.OTHER to "layers",
        )

        val EXTRA_CATEGORIES = listOf(
            Triple("Kopi", "coffee", CategoryKind.EXPENSE),
            Triple("Jajan", "cookie", CategoryKind.EXPENSE),
            Triple("Parkir", "circle-parking", CategoryKind.EXPENSE),
            Triple("Bensin", "fuel", CategoryKind.EXPENSE),
            Triple("Pulsa & Data", "smartphone", CategoryKind.EXPENSE),
            Triple("Langganan", "repeat", CategoryKind.EXPENSE),
            Triple("Olahraga", "dumbbell", CategoryKind.EXPENSE),
            Triple("Pendidikan", "graduation-cap", CategoryKind.EXPENSE),
            Triple("Hadiah", "gift", CategoryKind.EXPENSE),
            Triple("Rumah Tangga", "house", CategoryKind.EXPENSE),
            Triple("Perawatan Diri", "sparkles", CategoryKind.EXPENSE),
            Triple("Hewan Peliharaan", "paw-print", CategoryKind.EXPENSE),
            Triple("Donasi", "hand-heart", CategoryKind.EXPENSE),
            Triple("Bonus", "award", CategoryKind.INCOME),
            Triple("Freelance", "laptop", CategoryKind.INCOME),
        )
    }
}
