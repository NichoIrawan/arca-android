package org.nicho.arca.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// SDD-ARC-002 §6.1. Field names are the wire contract (§6.6): never rename one without
// keeping the old name in the mapper.
@Entity(
    tableName = "accounts",
    indices = [Index("isArchived"), Index("sortOrder")],
)
internal data class AccountEntity(
    @PrimaryKey val id: String,          // lowercase UUID string, generated client-side
    val name: String,
    val type: String,                    // AccountType.code
    val openingBalanceMinor: Long,       // may be negative
    val colorHex: String,
    val icon: String,
    val isArchived: Boolean,
    val isFavorite: Boolean,             // FR-205
    val sortOrder: Int,                  // FR-204
    val createdAt: Long,                 // epoch millis, UTC
    val updatedAt: Long,                 // sync last-write-wins (FR-321)
)
