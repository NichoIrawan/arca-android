package org.nicho.arca.testing

import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.nicho.arca.domain.error.DomainError
import org.nicho.arca.domain.error.PersistenceError
import org.nicho.arca.domain.model.BudgetAllocation
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.repository.BudgetStore

// Same contract as RoomBudgetStore: one allocation per (category, month), and the category's
// current budget always equals its latest allocation.
class InMemoryBudgetStore internal constructor(
    private val ledger: InMemoryLedger,
) : BudgetStore {

    override fun observeAllocations(): Flow<List<BudgetAllocation>> =
        ledger.allocationRows.map { all -> all.sortedWith(compareBy({ it.categoryId.value }, { it.month })) }

    override suspend fun setBudget(id: CategoryId, from: YearMonth, amountMinor: Long?) {
        val category = budgetable(id)
        val others = ledger.allocationRows.value.filterNot { it.categoryId == id && it.month == from }
        ledger.allocationRows.value = others + BudgetAllocation(id, from, amountMinor)
        ledger.categories.replace(category.copy(budgetMinor = amountMinor))
    }

    override suspend fun setRollsOver(id: CategoryId, rollsOver: Boolean) {
        ledger.categories.replace(budgetable(id).copy(rollsOver = rollsOver))
    }

    private fun budgetable(id: CategoryId): Category {
        val category = ledger.categoryRows.value.firstOrNull { it.id == id }
            ?: throw PersistenceError.SaveFailed(NoSuchElementException("No category with id $id"))
        if (category.isSystemLocked) throw DomainError.CategoryIsSystemLocked()
        if (category.kind != CategoryKind.EXPENSE) throw DomainError.CategoryNotBudgeTable()
        return category
    }
}
