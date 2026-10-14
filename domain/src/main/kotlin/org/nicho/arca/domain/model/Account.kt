package org.nicho.arca.domain.model

// SDD-ARC-002 §7.1 · SRS §6.1. Name ≤ 40 chars; openingBalanceMinor may be negative.
data class Account(
    val id: AccountId,
    val name: String,
    val type: AccountType,
    val openingBalanceMinor: Long,
    val colorHex: String,
    val icon: String,
    val isArchived: Boolean,
    val isFavorite: Boolean,
    val sortOrder: Int,
)
