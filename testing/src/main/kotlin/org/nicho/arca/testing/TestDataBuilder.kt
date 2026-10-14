package org.nicho.arca.testing

import java.time.Instant
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

// Terse builders: a test names only the fields it's about.

fun testAccount(
    name: String = "BCA",
    type: AccountType = AccountType.BANK,
    openingBalanceMinor: Long = 0,
    isArchived: Boolean = false,
    isFavorite: Boolean = false,
    sortOrder: Int = 0,
    id: AccountId = AccountId.random(),
) = Account(
    id = id,
    name = name,
    type = type,
    openingBalanceMinor = openingBalanceMinor,
    colorHex = "#3260a3",
    icon = "landmark",
    isArchived = isArchived,
    isFavorite = isFavorite,
    sortOrder = sortOrder,
)

fun testCategory(
    name: String = "Makanan & Minuman",
    kind: CategoryKind = CategoryKind.EXPENSE,
    rollsOver: Boolean = false,
    isArchived: Boolean = false,
    isSystemLocked: Boolean = false,
    budgetMinor: Long? = null,
    id: CategoryId = CategoryId.random(),
) = Category(
    id = id,
    name = name,
    icon = "tag",
    colorHex = "#c56a3e",
    kind = kind,
    isArchived = isArchived,
    isSystemLocked = isSystemLocked,
    budgetMinor = budgetMinor,
    rollsOver = rollsOver,
)

fun testExpense(account: Account, category: Category, amountMinor: Long, date: Instant) =
    transaction(TransactionType.EXPENSE, account.id, amountMinor, date, categoryId = category.id)

fun testIncome(account: Account, category: Category, amountMinor: Long, date: Instant) =
    transaction(TransactionType.INCOME, account.id, amountMinor, date, categoryId = category.id)

/** A transfer's members, exactly as TransferService builds them: out, in, and an optional fee. */
fun testTransfer(
    from: Account,
    to: Account,
    amountMinor: Long,
    date: Instant,
    fee: Pair<Long, Category>? = null,
    groupId: TransferGroupId = TransferGroupId.random(),
): List<Transaction> = listOfNotNull(
    transaction(TransactionType.TRANSFER_OUT, from.id, amountMinor, date, groupId = groupId, counterAccountId = to.id),
    transaction(TransactionType.TRANSFER_IN, to.id, amountMinor, date, groupId = groupId, counterAccountId = from.id),
    fee?.let { (feeMinor, fees) ->
        transaction(TransactionType.EXPENSE, from.id, feeMinor, date, categoryId = fees.id, groupId = groupId)
    },
)

private fun transaction(
    type: TransactionType,
    accountId: AccountId,
    amountMinor: Long,
    date: Instant,
    categoryId: CategoryId? = null,
    groupId: TransferGroupId? = null,
    counterAccountId: AccountId? = null,
) = Transaction(
    id = TransactionId.random(),
    amountMinor = amountMinor,
    type = type,
    date = date,
    note = null,
    accountId = accountId,
    categoryId = categoryId,
    transferGroupId = groupId,
    counterAccountId = counterAccountId,
)
