package org.nicho.arca.feature.accounts

import org.nicho.arca.domain.model.AccountType

enum class AccountEditorIssue { NAME_REQUIRED, NAME_TOO_LONG, OPENING_UNCLEAR, SAVE_FAILED }

data class AccountEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val type: AccountType = AccountType.BANK,
    val icon: String = "landmark",
    val iconPicked: Boolean = false,          // once picked, changing the type stops changing the icon
    val openingText: String = "0",
    val openingLocked: Boolean = false,       // once the account has transactions (prototype)
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val currentBalanceMinor: Long? = null,    // editing only: calculated, never typed
    val canDelete: Boolean = false,           // no history: deleting loses nothing (FR-203)
    val issues: Set<AccountEditorIssue> = emptySet(),
    val isDone: Boolean = false,
)
