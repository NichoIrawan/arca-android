package org.nicho.arca.testing

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.nicho.arca.domain.error.PersistenceError
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.BalanceRow
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.Period
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionId
import org.nicho.arca.domain.model.TransactionListItem
import org.nicho.arca.domain.model.TransferGroupId
import org.nicho.arca.domain.repository.TransactionReading
import org.nicho.arca.domain.repository.TransactionWriting
import org.nicho.arca.domain.repository.TransferPersisting

class InMemoryTransactionStore internal constructor(
    private val ledger: InMemoryLedger,
) : TransactionReading, TransactionWriting, TransferPersisting {

    /**
     * Fault injection (SDD-ARC-002 §14.1): the next write throws PersistenceError.SaveFailed and
     * changes nothing, the way a rolled-back Room transaction would.
     */
    var failNextWrite = false

    private val rows get() = ledger.transactionRows

    // ── Reading ──

    override fun observeInPeriod(period: Period): Flow<List<Transaction>> = rows.map { all ->
        all.filter { it.date >= period.start && it.date < period.endExclusive }.newestFirst()
    }

    override fun observeForAccount(id: AccountId): Flow<List<Transaction>> =
        rows.map { all -> all.filter { it.accountId == id }.newestFirst() }

    override fun observeRecent(limit: Int): Flow<List<TransactionListItem>> =
        combine(rows, ledger.accountRows, ledger.categoryRows) { all, accounts, categories ->
            val accountsById = accounts.associateBy { it.id }
            val categoriesById = categories.associateBy { it.id }
            all.newestFirst().take(limit).map { tx ->
                val account = accountsById.getValue(tx.accountId)
                val category = tx.categoryId?.let(categoriesById::get)
                TransactionListItem(
                    transaction = tx,
                    accountName = account.name,
                    accountColorHex = account.colorHex,
                    counterAccountName = tx.counterAccountId?.let { accountsById[it]?.name },
                    categoryName = category?.name,
                    categoryIcon = category?.icon,
                    categoryColorHex = category?.colorHex,
                )
            }
        }

    override fun observeBalanceRows(): Flow<List<BalanceRow>> =
        rows.map { all -> all.map { BalanceRow(it.accountId, it.amountMinor, it.type) } }

    override fun observeHasAny(): Flow<Boolean> = rows.map { it.isNotEmpty() }

    override suspend fun byId(id: TransactionId): Transaction? = rows.value.firstOrNull { it.id == id }

    override fun observeById(id: TransactionId): Flow<Transaction?> = rows.map { all -> all.firstOrNull { it.id == id } }

    override fun observeGroup(groupId: TransferGroupId): Flow<List<Transaction>> =
        rows.map { all -> all.filter { it.transferGroupId == groupId } }

    override suspend fun allForExport(): List<Transaction> = rows.value.sortedBy { it.date }

    override suspend fun count(): Int = rows.value.size

    override fun observeCountsByCategory(): Flow<Map<CategoryId, Int>> =
        rows.map { all -> all.mapNotNull { it.categoryId }.groupingBy { it }.eachCount() }

    override fun observeEarliestDate(): Flow<Instant?> = rows.map { all -> all.minOfOrNull { it.date } }

    // ── Writing ──

    override suspend fun insert(tx: Transaction) = write { it + checked(tx) }

    override suspend fun update(tx: Transaction) = write { all ->
        if (all.none { it.id == tx.id }) throw missing(tx.id)
        all.map { if (it.id == tx.id) checked(tx) else it }
    }

    override suspend fun delete(ids: List<TransactionId>) = write { all -> all.filterNot { it.id in ids } }

    override suspend fun insertAtomic(txs: List<Transaction>) = write { it + txs.map(::checked) }

    override suspend fun replaceGroup(groupId: TransferGroupId, txs: List<Transaction>) = write { all ->
        all.filterNot { it.transferGroupId == groupId } + txs.map(::checked)
    }

    override suspend fun deleteAll(groupId: TransferGroupId) = write { all ->
        all.filterNot { it.transferGroupId == groupId }
    }

    override suspend fun inGroup(groupId: TransferGroupId): List<Transaction> =
        rows.value.filter { it.transferGroupId == groupId }

    // ── Internals ──

    private fun write(change: (List<Transaction>) -> List<Transaction>) {
        if (failNextWrite) {
            failNextWrite = false
            throw PersistenceError.SaveFailed(IllegalStateException("Injected write failure"))
        }
        rows.value = change(rows.value)
    }

    /** What Room's foreign keys check: the referenced account and category exist. */
    private fun checked(tx: Transaction): Transaction {
        val accounts = ledger.accountRows.value.map { it.id }.toSet()
        val categories = ledger.categoryRows.value.map { it.id }.toSet()
        val ok = tx.accountId in accounts &&
            (tx.counterAccountId == null || tx.counterAccountId in accounts) &&
            (tx.categoryId == null || tx.categoryId in categories)
        if (!ok) throw PersistenceError.SaveFailed(IllegalStateException("Foreign key violation: $tx"))
        return tx
    }

    private fun missing(id: TransactionId) =
        PersistenceError.SaveFailed(NoSuchElementException("No transaction with id $id"))

    private fun List<Transaction>.newestFirst() = sortedByDescending { it.date }
}
