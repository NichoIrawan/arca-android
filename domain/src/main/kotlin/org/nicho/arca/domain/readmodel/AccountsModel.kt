package org.nicho.arca.domain.readmodel

import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.AccountType

// What the Accounts (Akun) tab shows (FR-204, FR-220, FR-221).

data class AccountBalance(
    val account: Account,
    val balanceMinor: Long,       // may be negative (FR-132 note); shown with its sign (FR-222)
)

data class AccountGroup(
    val type: AccountType,
    val accounts: List<AccountBalance>,
    val subtotalMinor: Long,
)

data class AccountsModel(
    val groups: List<AccountGroup>,      // cash, bank, e-wallet, other; empty types left out
    val archived: List<AccountBalance>,  // not in any group or in the net position (FR-202, FR-221)
    val netPositionMinor: Long,
)
