package org.nicho.arca.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.nicho.arca.domain.error.DomainError
import org.nicho.arca.domain.error.PersistenceError
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.seeding.DefaultCategoryProvider

// Same contract as RoomCategoryStore: appearance-only updates, unbudgeted inserts, Fees locked.
class InMemoryCategoryStore internal constructor(
    private val ledger: InMemoryLedger,
) : CategoryStore {

    private val rows get() = ledger.categoryRows

    override fun observeAll(includeArchived: Boolean): Flow<List<Category>> = rows.map { all ->
        all.filter { includeArchived || !it.isArchived }.sortedBy { it.name }
    }

    override suspend fun byId(id: CategoryId): Category? = rows.value.firstOrNull { it.id == id }

    override suspend fun feesCategory(): Category =
        rows.value.firstOrNull { it.isSystemLocked } ?: throw DomainError.FeesCategoryMissing()

    override suspend fun seedDefaultsIfNeeded() {
        if (rows.value.none { it.isSystemLocked }) rows.value = rows.value + DefaultCategoryProvider.defaults()
    }

    override suspend fun insert(category: Category) {
        require(!category.isSystemLocked) { "Only seeding creates the system-locked category" }
        require(category.budgetMinor == null && !category.rollsOver) {
            "A new category starts unbudgeted; set its budget through BudgetStore"
        }
        rows.value = rows.value + category
    }

    override suspend fun update(category: Category) {
        val existing = byId(category.id) ?: throw missing(category.id)
        replace(existing.copy(name = category.name, icon = category.icon, colorHex = category.colorHex))
    }

    override suspend fun archive(id: CategoryId) {
        val existing = byId(id) ?: throw missing(id)
        replace(existing.copy(isArchived = true))
    }

    override suspend fun delete(id: CategoryId) {
        val existing = byId(id) ?: return
        if (existing.isSystemLocked) throw DomainError.CategoryIsSystemLocked()
        if (ledger.transactionRows.value.any { it.categoryId == id }) throw DomainError.CategoryHasTransactions()
        rows.value = rows.value.filterNot { it.id == id }
        ledger.allocationRows.value = ledger.allocationRows.value.filterNot { it.categoryId == id } // CASCADE
    }

    internal fun replace(category: Category) {
        rows.value = rows.value.map { if (it.id == category.id) category else it }
    }

    private fun missing(id: CategoryId) = PersistenceError.SaveFailed(NoSuchElementException("No category with id $id"))
}
