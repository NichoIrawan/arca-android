package org.nicho.arca.data.mapper

import java.time.Instant
import org.nicho.arca.data.db.entities.BalanceRowEntity
import org.nicho.arca.data.db.entities.TransactionEntity
import org.nicho.arca.data.db.entities.TransactionListItemRow
import org.nicho.arca.domain.model.BalanceRow
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionListItem
import org.nicho.arca.domain.model.TransactionType

// Instant in the domain, epoch millis (UTC) in storage (DD-A18).

internal fun TransactionEntity.toDomain() = Transaction(
    id = id.toTransactionId(),
    amountMinor = amountMinor,
    type = TransactionType.fromCode(type),
    date = Instant.ofEpochMilli(date),
    note = note,
    accountId = accountId.toAccountId(),
    categoryId = categoryId?.toCategoryId(),
    transferGroupId = transferGroupId?.toTransferGroupId(),
    counterAccountId = counterAccountId?.toAccountId(),
)

internal fun Transaction.toEntity(createdAt: Long, updatedAt: Long) = TransactionEntity(
    id = id.key(),
    amountMinor = amountMinor,
    type = type.code,
    date = date.toEpochMilli(),
    note = note,
    accountId = accountId.key(),
    categoryId = categoryId?.key(),
    transferGroupId = transferGroupId?.key(),
    counterAccountId = counterAccountId?.key(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun BalanceRowEntity.toDomain() = BalanceRow(
    accountId = accountId.toAccountId(),
    amountMinor = amountMinor,
    type = TransactionType.fromCode(type),
)

internal fun TransactionListItemRow.toDomain() = TransactionListItem(
    transaction = transaction.toDomain(),
    accountName = accountName,
    accountColorHex = accountColor,
    counterAccountName = counterAccountName,
    categoryName = categoryName,
    categoryIcon = categoryIcon,
    categoryColorHex = categoryColor,
)
