package org.nicho.arca.data.repository

import androidx.room.withTransaction
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.nicho.arca.data.db.ArcaDatabase
import org.nicho.arca.data.db.dao.TransactionDao
import org.nicho.arca.data.mapper.key
import org.nicho.arca.data.mapper.toDomain
import org.nicho.arca.data.mapper.toEntity
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.BalanceRow
import org.nicho.arca.domain.model.Period
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionId
import org.nicho.arca.domain.model.TransactionListItem
import org.nicho.arca.domain.model.TransferGroupId
import org.nicho.arca.domain.repository.TransactionReading
import org.nicho.arca.domain.repository.TransactionWriting
import org.nicho.arca.domain.repository.TransferPersisting

// SDD-ARC-002 §8.2. One class, three role interfaces: interface segregation limits what each
// *consumer* sees, not how many interfaces one implementation may satisfy.
internal class RoomTransactionStore @Inject constructor(
    private val db: ArcaDatabase,
    private val dao: TransactionDao,
    private val clock: Clock,
) : TransactionReading, TransactionWriting, TransferPersisting {

    // ── Reading ──

    override fun observeInPeriod(period: Period): Flow<List<Transaction>> =
        dao.observeInPeriod(period.start.toEpochMilli(), period.endExclusive.toEpochMilli())
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeForAccount(id: AccountId): Flow<List<Transaction>> =
        dao.observeForAccount(id.key()).map { rows -> rows.map { it.toDomain() } }

    override fun observeRecent(limit: Int): Flow<List<TransactionListItem>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override fun observeBalanceRows(): Flow<List<BalanceRow>> =
        dao.observeBalanceRows().map { rows -> rows.map { it.toDomain() } }

    override suspend fun byId(id: TransactionId): Transaction? = dao.getById(id.key())?.toDomain()

    override suspend fun allForExport(): List<Transaction> = dao.getAll().map { it.toDomain() }

    override suspend fun count(): Int = dao.count()

    // ── Writing ──

    override suspend fun insert(tx: Transaction) = saveOrFail {
        val now = clock.millis()
        dao.insert(tx.toEntity(createdAt = now, updatedAt = now))
    }

    // Read-then-write in one transaction so createdAt survives the edit; it orders same-day rows.
    override suspend fun update(tx: Transaction) = saveOrFail {
        db.withTransaction {
            val existing = dao.getById(tx.id.key()) ?: throw missing("transaction", tx.id.key())
            dao.update(tx.toEntity(createdAt = existing.createdAt, updatedAt = clock.millis()))
        }
        Unit
    }

    override suspend fun delete(ids: List<TransactionId>) = saveOrFail {
        dao.delete(ids.map { it.key() })
    }

    // ── Transfers ──

    // DD-A09. Room already runs a list @Insert in one transaction; the explicit block states the
    // guarantee where a reader looks for it, and keeps it if this ever grows a second call.
    override suspend fun insertAtomic(txs: List<Transaction>) = saveOrFail {
        db.withTransaction {
            val now = clock.millis()
            dao.insertAll(txs.map { it.toEntity(createdAt = now, updatedAt = now) })
        }
    }

    // One DELETE statement, so already atomic.
    override suspend fun deleteAll(groupId: TransferGroupId) = saveOrFail {
        dao.deleteGroup(groupId.key())
    }

    override suspend fun inGroup(groupId: TransferGroupId): List<Transaction> =
        dao.inGroup(groupId.key()).map { it.toDomain() }
}
