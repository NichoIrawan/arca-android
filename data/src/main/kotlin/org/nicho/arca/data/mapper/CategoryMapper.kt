package org.nicho.arca.data.mapper

import org.nicho.arca.data.db.entities.CategoryEntity
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryKind

internal fun CategoryEntity.toDomain() = Category(
    id = id.toCategoryId(),
    name = name,
    icon = icon,
    colorHex = colorHex,
    kind = CategoryKind.fromCode(kind),
    isArchived = isArchived,
    isSystemLocked = isSystemLocked,
    budgetMinor = budgetMinor,
    rollsOver = rollsOver,
)

internal fun Category.toEntity(createdAt: Long, updatedAt: Long) = CategoryEntity(
    id = id.key(),
    name = name,
    icon = icon,
    colorHex = colorHex,
    kind = kind.code,
    isArchived = isArchived,
    isSystemLocked = isSystemLocked,
    budgetMinor = budgetMinor,
    rollsOver = rollsOver,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
