package org.nicho.arca.data.repository

import androidx.room.withTransaction
import java.time.Clock
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.nicho.arca.data.db.ArcaDatabase
import org.nicho.arca.data.db.dao.BudgetAllocationDao
import org.nicho.arca.data.db.dao.CategoryDao
import org.nicho.arca.data.db.entities.BudgetAllocationEntity
import org.nicho.arca.data.mapper.key
import org.nicho.arca.data.mapper.toDomain
import org.nicho.arca.domain.error.DomainError
import org.nicho.arca.domain.model.BudgetAllocation
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.repository.BudgetStore

// SDD-ARC-002 §8.8. No arithmetic here: carry, available and left are EnvelopeCalculator's.
internal class RoomBudgetStore @Inject constructor(
    private val db: ArcaDatabase,
    private val allocations: BudgetAllocationDao,
    private val categories: CategoryDao,
    private val clock: Clock,
) : BudgetStore {

    override fun observeAllocations(): Flow<List<BudgetAllocation>> =
        allocations.observeAll().map { rows -> rows.map { it.toDomain() } }

    // The category's current budget and the month's allocation row are one commit, so a process
    // kill can't leave them disagreeing; a throw rolls back both.
    override suspend fun setBudget(id: CategoryId, from: YearMonth, amountMinor: Long?) = saveOrFail {
        db.withTransaction {
            val now = clock.millis()
            if (categories.setBudgetMinor(id.key(), amountMinor, now) == 0) throw refusal(id)
            allocations.upsertForMonth(
                BudgetAllocationEntity(
                    id = UUID.randomUUID().toString(), // kept only if the month has no row yet
                    categoryId = id.key(),
                    month = from.key(),
                    amountMinor = amountMinor,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
    }

    override suspend fun setRollsOver(id: CategoryId, rollsOver: Boolean) = saveOrFail {
        if (categories.setRollsOver(id.key(), rollsOver, clock.millis()) == 0) throw refusal(id)
    }

    // The DAO guard only says "0 rows". Look once more to say why, so the UI can explain it.
    private suspend fun refusal(id: CategoryId): Exception {
        val category = categories.getById(id.key()) ?: return missing("category", id.key())
        return if (category.isSystemLocked) DomainError.CategoryIsSystemLocked() else DomainError.CategoryNotBudgeTable()
    }
}
