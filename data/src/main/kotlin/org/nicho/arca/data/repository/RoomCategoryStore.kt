package org.nicho.arca.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.nicho.arca.data.db.ArcaDatabase
import org.nicho.arca.data.db.dao.CategoryDao
import org.nicho.arca.data.mapper.key
import org.nicho.arca.data.mapper.toDomain
import org.nicho.arca.data.mapper.toEntity
import org.nicho.arca.domain.error.DomainError
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.seeding.DefaultCategoryProvider

internal class RoomCategoryStore @Inject constructor(
    private val db: ArcaDatabase,
    private val dao: CategoryDao,
    private val clock: Clock,
) : CategoryStore {

    override fun observeAll(includeArchived: Boolean): Flow<List<Category>> =
        dao.observeAll(includeArchived).map { rows -> rows.map { it.toDomain() } }

    override suspend fun byId(id: CategoryId): Category? = dao.getById(id.key())?.toDomain()

    override suspend fun feesCategory(): Category =
        dao.getSystemLocked()?.toDomain() ?: throw DomainError.FeesCategoryMissing()

    // SDD-ARC-002 §8.7. Idempotent by looking at the data, not at a "seeded" flag: a flag can
    // disagree with the data (set, then the seed is interrupted; or a backup restores the data
    // without the flag). The check and the insert share one transaction, so two concurrent
    // callers can't both seed.
    override suspend fun seedDefaultsIfNeeded() = saveOrFail {
        db.withTransaction {
            if (!dao.hasSystemLocked()) {
                val now = clock.millis()
                dao.insertAll(DefaultCategoryProvider.defaults().map { it.toEntity(createdAt = now, updatedAt = now) })
            }
        }
    }

    // Programming errors, so they fail loudly instead of being quietly corrected.
    override suspend fun insert(category: Category) {
        require(!category.isSystemLocked) { "Only seeding creates the system-locked category" }
        require(category.budgetMinor == null && !category.rollsOver) {
            "A new category starts unbudgeted; set its budget through BudgetStore"
        }
        saveOrFail {
            val now = clock.millis()
            dao.insert(category.toEntity(createdAt = now, updatedAt = now))
        }
    }

    override suspend fun update(category: Category) = saveOrFail {
        val changed = dao.updateDetails(
            id = category.id.key(),
            name = category.name,
            icon = category.icon,
            colorHex = category.colorHex,
            now = clock.millis(),
        )
        if (changed == 0) throw missing("category", category.id.key())
    }

    override suspend fun archive(id: CategoryId) = saveOrFail {
        if (dao.archive(id.key(), clock.millis()) == 0) throw missing("category", id.key())
    }

    override suspend fun unarchive(id: CategoryId) = saveOrFail {
        if (dao.unarchive(id.key(), clock.millis()) == 0) throw missing("category", id.key())
    }

    override suspend fun delete(id: CategoryId) = saveOrFail {
        db.withTransaction {
            val existing = dao.getById(id.key()) ?: return@withTransaction // already gone
            if (existing.isSystemLocked) throw DomainError.CategoryIsSystemLocked()
            try {
                dao.delete(id.key())
            } catch (e: SQLiteConstraintException) {
                throw DomainError.CategoryHasTransactions()
            }
            Unit
        }
    }
}
