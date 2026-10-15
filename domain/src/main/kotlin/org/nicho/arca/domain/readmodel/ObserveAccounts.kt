package org.nicho.arca.domain.readmodel

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import org.nicho.arca.domain.calculation.BalanceCalculator
import org.nicho.arca.domain.model.AccountType
import org.nicho.arca.domain.repository.AccountReading
import org.nicho.arca.domain.repository.TransactionReading

/**
 * The Accounts tab: accounts grouped by type, each group with its subtotal and its share of the
 * money held. Groups are ordered largest subtotal first (the prototype's order); within a group,
 * accounts keep the store's order (sortOrder, then name). One balance-row observer serves every
 * account, rather than one query per account (DD-A10).
 */
class ObserveAccounts(
    private val transactions: TransactionReading,
    private val accounts: AccountReading,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    operator fun invoke(): Flow<AccountsModel> =
        combine(
            transactions.observeBalanceRows(),
            accounts.observeAll(includeArchived = true),
        ) { rows, allAccounts ->
            val balances = BalanceCalculator.balances(rows, allAccounts)
            val subtotals = BalanceCalculator.subtotalsByType(allAccounts, balances)
            val shares = BalanceCalculator.composition(subtotals)
            val withBalances = allAccounts.map { AccountBalance(it, balances.getValue(it.id)) }
            val (archived, active) = withBalances.partition { it.account.isArchived }
            AccountsModel(
                groups = AccountType.entries
                    .mapNotNull { type ->
                        val members = active.filter { it.account.type == type }
                        if (members.isEmpty()) null else AccountGroup(type, members, subtotals.getValue(type), shares[type] ?: 0f)
                    }
                    .sortedByDescending { it.subtotalMinor },
                favorites = active.filter { it.account.isFavorite },
                archived = archived,
                netPositionMinor = BalanceCalculator.netPosition(allAccounts, balances),
            )
        }.flowOn(dispatcher)
}
