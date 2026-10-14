package org.nicho.arca.domain.model

// SDD-ARC-002 §7.1 · SRS §6.1. Name ≤ 30 chars. kind is set at creation and never changes
// (DD-A21). budgetMinor is null for Fees and for income categories (FR-260); it and rollsOver
// are written only through BudgetStore, so they always agree with the allocation history.
data class Category(
    val id: CategoryId,
    val name: String,
    val icon: String,
    val colorHex: String,
    val kind: CategoryKind,
    val isArchived: Boolean,
    val isSystemLocked: Boolean,
    val budgetMinor: Long?,
    val rollsOver: Boolean,
)
