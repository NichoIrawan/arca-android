package org.nicho.arca.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// SDD-ARC-002 §6.1, DD-A19. One row per (category, month) in which the budget changed.
// CASCADE, not RESTRICT: an allocation means nothing without its category, and a category
// with transactions can't be deleted anyway (RESTRICT on transactions.categoryId).
@Entity(
    tableName = "budget_allocations",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["categoryId", "month"], unique = true)],
)
internal data class BudgetAllocationEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val month: String,                   // "yyyy-MM": fixed width, so it sorts and compares as text
    val amountMinor: Long?,              // > 0, or null = budget removed from this month forward
    val createdAt: Long,
    val updatedAt: Long,
)
