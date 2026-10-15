package org.nicho.arca.domain.readmodel

import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.AccountType

// What the Accounts (Akun) tab shows (FR-204, FR-205, FR-220, FR-221).

data class AccountBalance(
    val account: Account,
    val balanceMinor: Long,       // may be negative (FR-132 note); shown with its sign (FR-222)
)

data class AccountGroup(
    val type: AccountType,
    val accounts: List<AccountBalance>,
    val subtotalMinor: Long,
    val share: Float,             // of the money held, for the composition bar; geometry only
)

data class AccountsModel(
    val groups: List<AccountGroup>,      // largest subtotal first, as the prototype orders them
    val favorites: List<AccountBalance>, // FR-205
    val archived: List<AccountBalance>,  // not in any group or in the net position (FR-202, FR-221)
    val netPositionMinor: Long,
)
