package org.nicho.arca.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.AccountType
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionId
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.model.TransferGroupId

// Domain-object builders for store tests. Stores map ids through UUID, so unlike the entity
// builders in TestEntities.kt these use real UUIDs.

/** A clock the test moves by hand, so "updatedAt changed, createdAt didn't" is checkable. */
internal class TestClock(var millis: Long = T0) : Clock() {
    override fun instant(): Instant = Instant.ofEpochMilli(millis)
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
}

internal fun domainAccount(
    id: AccountId = AccountId.random(),
    name: String = "BCA",
    type: AccountType = AccountType.BANK,
    isFavorite: Boolean = false,
) = Account(
    id = id,
    name = name,
    type = type,
    openingBalanceMinor = 1_000_000,
    colorHex = "#3260a3",
    icon = "landmark",
    isArchived = false,
    isFavorite = isFavorite,
    sortOrder = 0,
)

internal fun domainCategory(
    id: CategoryId = CategoryId.random(),
    name: String = "Makanan & Minuman",
    kind: CategoryKind = CategoryKind.EXPENSE,
) = Category(
    id = id,
    name = name,
    icon = "utensils",
    colorHex = "#c56a3e",
    kind = kind,
    isArchived = false,
    isSystemLocked = false,
    budgetMinor = null,
    rollsOver = false,
)

internal fun domainTransaction(
    accountId: AccountId,
    categoryId: CategoryId?,
    id: TransactionId = TransactionId.random(),
    amountMinor: Long = 50_000,
    type: TransactionType = TransactionType.EXPENSE,
    date: Instant = Instant.ofEpochMilli(T0),
    transferGroupId: TransferGroupId? = null,
    counterAccountId: AccountId? = null,
    note: String? = null,
) = Transaction(
    id = id,
    amountMinor = amountMinor,
    type = type,
    date = date,
    note = note,
    accountId = accountId,
    categoryId = categoryId,
    transferGroupId = transferGroupId,
    counterAccountId = counterAccountId,
)
