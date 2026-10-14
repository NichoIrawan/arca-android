package org.nicho.arca.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// SDD-ARC-002 §6.1. A category is also an envelope (DD-A19): the budget lives here,
// its history lives in budget_allocations.
@Entity(
    tableName = "categories",
    indices = [Index("isArchived")],
)
internal data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val colorHex: String,
    val kind: String,                    // CategoryKind.code: "expense" | "income"; immutable (DD-A21)
    val isArchived: Boolean,
    val isSystemLocked: Boolean,         // true only for Fees (FR-250)
    val budgetMinor: Long?,              // FR-260; null = not budgeted. Always null for Fees and income.
    val rollsOver: Boolean = false,      // FR-261
    val createdAt: Long,
    val updatedAt: Long,
)
