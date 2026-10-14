package org.nicho.arca.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.nicho.arca.data.db.ArcaDatabase
import org.nicho.arca.data.db.dao.AccountDao
import org.nicho.arca.data.mapper.key
import org.nicho.arca.data.mapper.toDomain
import org.nicho.arca.data.mapper.toEntity
import org.nicho.arca.domain.error.DomainError
import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.repository.AccountReading
import org.nicho.arca.domain.repository.AccountWriting

internal class RoomAccountStore @Inject constructor(
    private val db: ArcaDatabase,
    private val dao: AccountDao,
    private val clock: Clock,
) : AccountReading, AccountWriting {

    override fun observeAll(includeArchived: Boolean): Flow<List<Account>> =
        dao.observeAll(includeArchived).map { rows -> rows.map { it.toDomain() } }

    override fun observeFavorites(): Flow<List<Account>> =
        dao.observeFavorites().map { rows -> rows.map { it.toDomain() } }

    override suspend fun byId(id: AccountId): Account? = dao.getById(id.key())?.toDomain()

    override suspend fun hasTransactions(id: AccountId): Boolean = dao.hasTransactions(id.key())

    override suspend fun insert(account: Account) = saveOrFail {
        val now = clock.millis()
        dao.insert(account.toEntity(createdAt = now, updatedAt = now))
    }

    override suspend fun update(account: Account) = saveOrFail {
        db.withTransaction {
            val existing = dao.getById(account.id.key()) ?: throw missing("account", account.id.key())
            dao.update(account.toEntity(createdAt = existing.createdAt, updatedAt = clock.millis()))
        }
        Unit
    }

    override suspend fun archive(id: AccountId) = saveOrFail {
        if (dao.archive(id.key(), clock.millis()) == 0) throw missing("account", id.key())
    }

    // FR-203: the FK on transactions refuses the delete; that becomes the blocked-action error
    // whose UI offers "archive instead" (§13). Deleting an id that's already gone is a no-op:
    // the row is absent either way.
    override suspend fun delete(id: AccountId) = saveOrFail {
        try {
            dao.delete(id.key())
        } catch (e: SQLiteConstraintException) {
            throw DomainError.AccountHasTransactions()
        }
        Unit
    }
}
