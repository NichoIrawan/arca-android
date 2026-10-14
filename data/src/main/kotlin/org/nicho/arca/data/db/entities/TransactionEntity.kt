package org.nicho.arca.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// SDD-ARC-002 §6.1.
// RESTRICT is FR-203 at the persistence layer: SQLite itself refuses to delete an account or
// category that still has transactions. Every FK column is indexed so a parent delete does
// not scan this table.
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["counterAccountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("date"),
        Index("type"),
        Index("transferGroupId"),
        Index("accountId"),
        Index("categoryId"),
        Index("counterAccountId"),
        Index(value = ["accountId", "date"]), // FR-107 per-account history
    ],
)
internal data class TransactionEntity(
    @PrimaryKey val id: String,
    val amountMinor: Long,               // always > 0; direction comes from type (DD-11)
    val type: String,                    // TransactionType.code
    val date: Long,                      // epoch millis, UTC
    val note: String?,
    val accountId: String,
    val categoryId: String?,             // null for transferOut / transferIn
    val transferGroupId: String?,        // shared by all members of a transfer, fee row included (FR-133)
    val counterAccountId: String?,       // null on a fee row
    val createdAt: Long,
    val updatedAt: Long,
)
