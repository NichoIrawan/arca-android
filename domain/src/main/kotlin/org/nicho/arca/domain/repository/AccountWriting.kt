package org.nicho.arca.domain.repository

import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.AccountId

// SDD-ARC-002 §7.2. delete throws DomainError.AccountHasTransactions when the account has
// history (FR-203); archive is the permitted alternative.
interface AccountWriting {
    suspend fun insert(account: Account)
    suspend fun update(account: Account)
    suspend fun archive(id: AccountId)
    suspend fun delete(id: AccountId)
}
