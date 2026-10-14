package org.nicho.arca.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.nicho.arca.domain.error.DomainError
import org.nicho.arca.domain.error.PersistenceError
import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.repository.AccountReading
import org.nicho.arca.domain.repository.AccountWriting

class InMemoryAccountStore internal constructor(
    private val ledger: InMemoryLedger,
) : AccountReading, AccountWriting {

    private val rows get() = ledger.accountRows

    override fun observeAll(includeArchived: Boolean): Flow<List<Account>> = rows.map { all ->
        all.filter { includeArchived || !it.isArchived }.sortedWith(compareBy({ it.sortOrder }, { it.name }))
    }

    override fun observeFavorites(): Flow<List<Account>> = rows.map { all ->
        all.filter { it.isFavorite && !it.isArchived }.sortedWith(compareBy({ it.sortOrder }, { it.name }))
    }

    override suspend fun byId(id: AccountId): Account? = rows.value.firstOrNull { it.id == id }

    override suspend fun hasTransactions(id: AccountId): Boolean =
        ledger.transactionRows.value.any { it.accountId == id || it.counterAccountId == id }

    override suspend fun insert(account: Account) {
        rows.value = rows.value + account
    }

    override suspend fun update(account: Account) {
        if (rows.value.none { it.id == account.id }) throw missing(account.id)
        rows.value = rows.value.map { if (it.id == account.id) account else it }
    }

    override suspend fun archive(id: AccountId) {
        val existing = byId(id) ?: throw missing(id)
        update(existing.copy(isArchived = true))
    }

    // Mirrors FK RESTRICT (FR-203).
    override suspend fun delete(id: AccountId) {
        if (hasTransactions(id)) throw DomainError.AccountHasTransactions()
        rows.value = rows.value.filterNot { it.id == id }
    }

    private fun missing(id: AccountId) = PersistenceError.SaveFailed(NoSuchElementException("No account with id $id"))
}
