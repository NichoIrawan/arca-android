package org.nicho.arca.domain.repository

import kotlinx.coroutines.flow.Flow
import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.AccountId

// SDD-ARC-002 §7.2.
interface AccountReading {
    fun observeAll(includeArchived: Boolean): Flow<List<Account>>
    fun observeFavorites(): Flow<List<Account>>
    suspend fun byId(id: AccountId): Account?
    suspend fun hasTransactions(id: AccountId): Boolean
}
