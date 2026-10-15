package org.nicho.arca.domain.readmodel

import java.time.LocalDate
import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionListItem
import org.nicho.arca.domain.model.TransactionType

/**
 * A group of list rows under one date header, with that day's net from income and expenses
 * ("Yesterday −542,500"). [date] and [netMinor] are null when the list isn't in date order
 * (sorted by amount), so there are no day headers.
 */
data class TransactionDay(
    val date: LocalDate?,
    val netMinor: Long?,
    val items: List<TransactionListItem>,
)

/** How a transaction list is ordered (Filter: "Sort by"). */
enum class SortOrder { NEWEST, OLDEST, LARGEST }

/** Joins transactions with the display fields of their account and category. */
internal fun listItems(
    txs: List<Transaction>,
    accounts: Map<AccountId, Account>,
    categories: Map<CategoryId, Category>,
): List<TransactionListItem> = txs.mapNotNull { tx ->
    val account = accounts[tx.accountId] ?: return@mapNotNull null
    val category = tx.categoryId?.let(categories::get)
    TransactionListItem(
        transaction = tx,
        accountName = account.name,
        accountColorHex = account.colorHex,
        counterAccountName = tx.counterAccountId?.let { accounts[it]?.name },
        categoryName = category?.name,
        categoryIcon = category?.icon,
        categoryColorHex = category?.colorHex,
    )
}

/**
 * A transfer is stored as two legs but listed as one "Transfer" row (the prototype): drop each
 * incoming leg whose outgoing leg is also in the list. An incoming leg on its own (a list
 * filtered to the destination account) stays.
 */
internal fun collapseTransferLegs(txs: List<Transaction>): List<Transaction> {
    val outgoing = txs.filter { it.type == TransactionType.TRANSFER_OUT }.mapNotNull { it.transferGroupId }.toSet()
    return txs.filterNot { it.type == TransactionType.TRANSFER_IN && it.transferGroupId in outgoing }
}

/** Orders [items] and, for date orders, groups them by day with each day's net. */
internal fun groupIntoDays(
    items: List<TransactionListItem>,
    sort: SortOrder,
    netByDay: Map<LocalDate, Long>,
    dayOf: (TransactionListItem) -> LocalDate,
): List<TransactionDay> = when (sort) {
    SortOrder.LARGEST -> {
        val sorted = items.sortedByDescending { it.transaction.amountMinor }
        if (sorted.isEmpty()) emptyList() else listOf(TransactionDay(null, null, sorted))
    }
    SortOrder.NEWEST, SortOrder.OLDEST -> {
        val ordered = if (sort == SortOrder.NEWEST) {
            items.sortedByDescending { it.transaction.date }
        } else {
            items.sortedBy { it.transaction.date }
        }
        ordered.groupBy(dayOf).map { (day, dayItems) -> TransactionDay(day, netByDay[day] ?: 0L, dayItems) }
    }
}
