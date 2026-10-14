package org.nicho.arca.domain.repository

import kotlinx.coroutines.flow.Flow
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId

// SDD-ARC-002 §7.2 · FR-133, FR-250–252.
// This store owns a category's identity and appearance. Its budget (budgetMinor, rollsOver)
// is written only through BudgetStore, so the current budget and its history can't disagree.
interface CategoryStore {
    fun observeAll(includeArchived: Boolean): Flow<List<Category>>
    suspend fun byId(id: CategoryId): Category?

    /** Throws DomainError.FeesCategoryMissing if absent: FR-133 can't work without it. */
    suspend fun feesCategory(): Category

    /** Seeds the defaults once. Safe to call on every launch (FR-250). */
    suspend fun seedDefaultsIfNeeded()

    /** New categories start unbudgeted and unlocked; set a budget afterwards via BudgetStore. */
    suspend fun insert(category: Category)

    /** Writes name, icon and colour only. kind is immutable (DD-A21). */
    suspend fun update(category: Category)

    suspend fun archive(id: CategoryId)

    /**
     * Throws DomainError.CategoryIsSystemLocked for Fees and DomainError.CategoryHasTransactions
     * for a category with history; archive is the permitted alternative in both cases.
     */
    suspend fun delete(id: CategoryId)
}
