package org.nicho.arca.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.nicho.arca.data.db.ArcaDatabase
import org.nicho.arca.data.db.entities.AccountEntity
import org.nicho.arca.data.db.entities.BudgetAllocationEntity
import org.nicho.arca.data.db.entities.CategoryEntity
import org.nicho.arca.data.db.entities.TransactionEntity

// Entity builders for DAO tests. Only what a test cares about needs to be passed.
// Readable ids ("bca") instead of UUIDs: SQLite doesn't care, and failures read better.

internal fun inMemoryDatabase(): ArcaDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ArcaDatabase::class.java)
        .allowMainThreadQueries()
        .build()

internal const val T0 = 1_760_000_000_000L // a fixed instant; tests never read the clock

internal fun account(
    id: String,
    name: String = id.uppercase(),
    isArchived: Boolean = false,
    isFavorite: Boolean = false,
    sortOrder: Int = 0,
) = AccountEntity(
    id = id,
    name = name,
    type = "bank",
    openingBalanceMinor = 0,
    colorHex = "#000000",
    icon = "landmark",
    isArchived = isArchived,
    isFavorite = isFavorite,
    sortOrder = sortOrder,
    createdAt = T0,
    updatedAt = T0,
)

internal fun category(
    id: String,
    name: String = id,
    kind: String = "expense",
    isSystemLocked: Boolean = false,
    isArchived: Boolean = false,
) = CategoryEntity(
    id = id,
    name = name,
    icon = "tag",
    colorHex = "#000000",
    kind = kind,
    isArchived = isArchived,
    isSystemLocked = isSystemLocked,
    budgetMinor = null,
    createdAt = T0,
    updatedAt = T0,
)

internal fun transaction(
    id: String,
    accountId: String,
    amountMinor: Long = 50_000,
    type: String = "expense",
    date: Long = T0,
    categoryId: String? = "food",
    transferGroupId: String? = null,
    counterAccountId: String? = null,
    createdAt: Long = date,
) = TransactionEntity(
    id = id,
    amountMinor = amountMinor,
    type = type,
    date = date,
    note = null,
    accountId = accountId,
    categoryId = categoryId,
    transferGroupId = transferGroupId,
    counterAccountId = counterAccountId,
    createdAt = createdAt,
    updatedAt = createdAt,
)

internal fun allocation(
    id: String,
    categoryId: String,
    month: String,
    amountMinor: Long?,
    at: Long = T0,
) = BudgetAllocationEntity(
    id = id,
    categoryId = categoryId,
    month = month,
    amountMinor = amountMinor,
    createdAt = at,
    updatedAt = at,
)

/**
 * JUnit's assertThrows takes a plain lambda, so it can't call a suspend DAO method.
 * Inline lambdas can, when the caller is already suspending (inside runTest).
 */
internal inline fun <reified T : Throwable> assertFails(block: () -> Unit) {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return
        throw AssertionError("Expected ${T::class.simpleName} but got $e", e)
    }
    throw AssertionError("Expected ${T::class.simpleName} but nothing was thrown")
}
