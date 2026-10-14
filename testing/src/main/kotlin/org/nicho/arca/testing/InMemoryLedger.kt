package org.nicho.arca.testing

import kotlinx.coroutines.flow.MutableStateFlow
import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.BudgetAllocation
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.Transaction

/**
 * In-memory versions of every store, sharing one set of tables so the cross-table rules hold the
 * way Room's foreign keys make them hold: a transaction needs its account and category, an
 * account with transactions can't be deleted.
 *
 * Each table is a StateFlow, so every observe* re-emits after a write, like Room's invalidation.
 * Writes build the new list first and assign it once, so a failed write changes nothing.
 *
 *     val ledger = InMemoryLedger()
 *     ledger.categories.seedDefaultsIfNeeded()
 *     ledger.accounts.insert(testAccount())
 */
class InMemoryLedger {
    internal val accountRows = MutableStateFlow<List<Account>>(emptyList())
    internal val categoryRows = MutableStateFlow<List<Category>>(emptyList())
    internal val transactionRows = MutableStateFlow<List<Transaction>>(emptyList())
    internal val allocationRows = MutableStateFlow<List<BudgetAllocation>>(emptyList())

    val accounts = InMemoryAccountStore(this)
    val categories = InMemoryCategoryStore(this)
    val transactions = InMemoryTransactionStore(this)
    val budgets = InMemoryBudgetStore(this)
}
