package org.nicho.arca.domain.repository

import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import org.nicho.arca.domain.model.BudgetAllocation
import org.nicho.arca.domain.model.CategoryId

// SDD-ARC-002 §7.2, §8.8, DD-A19. Called by BudgetService after validation; the store re-checks
// that the category is budgetable as defence in depth.
interface BudgetStore {
    fun observeAllocations(): Flow<List<BudgetAllocation>>

    /** FR-260, FR-265: writes the category's current budget and the allocation for `from`, together. */
    suspend fun setBudget(id: CategoryId, from: YearMonth, amountMinor: Long?)

    /** FR-261. */
    suspend fun setRollsOver(id: CategoryId, rollsOver: Boolean)
}
