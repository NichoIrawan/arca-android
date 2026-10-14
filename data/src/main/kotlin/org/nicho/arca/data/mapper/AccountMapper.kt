package org.nicho.arca.data.mapper

import org.nicho.arca.data.db.entities.AccountEntity
import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.AccountType

internal fun AccountEntity.toDomain() = Account(
    id = id.toAccountId(),
    name = name,
    type = AccountType.fromCode(type),
    openingBalanceMinor = openingBalanceMinor,
    colorHex = colorHex,
    icon = icon,
    isArchived = isArchived,
    isFavorite = isFavorite,
    sortOrder = sortOrder,
)

// Timestamps aren't part of the domain model; the store supplies them.
internal fun Account.toEntity(createdAt: Long, updatedAt: Long) = AccountEntity(
    id = id.key(),
    name = name,
    type = type.code,
    openingBalanceMinor = openingBalanceMinor,
    colorHex = colorHex,
    icon = icon,
    isArchived = isArchived,
    isFavorite = isFavorite,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
