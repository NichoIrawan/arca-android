package org.nicho.arca.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.domain.error.DomainError
import org.nicho.arca.domain.error.PersistenceError
import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.AccountType
import org.nicho.arca.domain.parsing.CurrencyParser
import org.nicho.arca.domain.readmodel.ObserveAccounts
import org.nicho.arca.domain.repository.AccountReading
import org.nicho.arca.domain.repository.AccountWriting
import org.nicho.arca.domain.seeding.DefaultCategoryProvider
import org.nicho.arca.navigation.AccountEditorRoute

/** New / edit account (FR-201–203, FR-205). Name ≤ 40 characters (SRS §6.1). */
@HiltViewModel
class AccountEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reading: AccountReading,
    private val writing: AccountWriting,
    private val observeAccounts: ObserveAccounts,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<AccountEditorRoute>()
    private val editingId = route.accountId?.let { AccountId(UUID.fromString(it)) }

    /** The account being edited, for the Adjust balance sheet; null for a new one. */
    val accountId: AccountId? get() = editingId
    private var original: Account? = null

    // A new account can arrive with its type chosen (the empty state's "Cash / Bank / E-Wallet").
    private val presetType = route.type?.let(AccountType::fromCode) ?: AccountType.BANK

    private val _state = MutableStateFlow(
        AccountEditorUiState(
            isLoading = editingId != null,
            isNew = editingId == null,
            type = presetType,
            icon = ArcaIcons.forAccountType(presetType),
        ),
    )
    val state: StateFlow<AccountEditorUiState> = _state.asStateFlow()

    init {
        if (editingId != null) {
            viewModelScope.launch {
                val account = reading.byId(editingId) ?: run { _state.update { it.copy(isDone = true) }; return@launch }
                original = account
                val hasHistory = reading.hasTransactions(editingId)
                val balance = observeAccounts().first().let { model ->
                    (model.groups.flatMap { it.accounts } + model.archived).firstOrNull { it.account.id == editingId }?.balanceMinor
                }
                _state.value = AccountEditorUiState(
                    isLoading = false,
                    isNew = false,
                    name = account.name,
                    type = account.type,
                    icon = account.icon,
                    iconPicked = true,
                    openingText = account.openingBalanceMinor.toString(),
                    openingLocked = hasHistory,
                    isFavorite = account.isFavorite,
                    isArchived = account.isArchived,
                    currentBalanceMinor = balance,
                    canDelete = !hasHistory,
                )
                // Keep the shown balance live, so an adjustment made from here shows at once.
                observeAccounts().collect { model ->
                    val now = (model.groups.flatMap { it.accounts } + model.archived).firstOrNull { it.account.id == editingId }?.balanceMinor
                    val history = reading.hasTransactions(editingId)
                    _state.update { it.copy(currentBalanceMinor = now, openingLocked = history, canDelete = !history) }
                }
            }
        }
    }

    fun onNameChange(name: String) = _state.update { it.copy(name = name, issues = it.issues - NAME_ISSUES) }

    fun onTypeChange(type: AccountType) = _state.update {
        it.copy(type = type, icon = if (it.iconPicked) it.icon else ArcaIcons.forAccountType(type))
    }

    fun onIconChange(icon: String) = _state.update { it.copy(icon = icon, iconPicked = true) }

    fun onOpeningChange(text: String) = _state.update { it.copy(openingText = text, issues = it.issues - AccountEditorIssue.OPENING_UNCLEAR) }

    fun onFavoriteChange(favorite: Boolean) = _state.update { it.copy(isFavorite = favorite) }

    fun onSave() {
        val form = _state.value
        val name = form.name.trim()
        val issues = buildSet {
            if (name.isEmpty()) add(AccountEditorIssue.NAME_REQUIRED)
            if (name.codePointCount(0, name.length) > NAME_MAX) add(AccountEditorIssue.NAME_TOO_LONG)
        }
        val opening = when (val parsed = CurrencyParser.parseBalance(form.openingText.ifBlank { "0" })) {
            is CurrencyParser.Result.Amount -> parsed.minor
            is CurrencyParser.Result.Invalid -> null
        }
        if (issues.isNotEmpty() || opening == null) {
            _state.update { it.copy(issues = issues + if (opening == null) setOf(AccountEditorIssue.OPENING_UNCLEAR) else emptySet()) }
            return
        }
        viewModelScope.launch {
            try {
                val base = original
                if (base == null) {
                    val count = reading.observeAll(includeArchived = true).first().size
                    writing.insert(
                        Account(
                            id = AccountId.random(),
                            name = name,
                            type = form.type,
                            openingBalanceMinor = opening,
                            colorHex = DefaultCategoryProvider.palette[count % DefaultCategoryProvider.palette.size],
                            icon = form.icon,
                            isArchived = false,
                            isFavorite = form.isFavorite,
                            sortOrder = count,
                        ),
                    )
                } else {
                    writing.update(
                        base.copy(
                            name = name,
                            type = form.type,
                            icon = form.icon,
                            isFavorite = form.isFavorite,
                            // Locked once there's history, so past balances stay as they were.
                            openingBalanceMinor = if (form.openingLocked) base.openingBalanceMinor else opening,
                        ),
                    )
                }
                _state.update { it.copy(isDone = true) }
            } catch (e: PersistenceError) {
                _state.update { it.copy(issues = it.issues + AccountEditorIssue.SAVE_FAILED) }
            }
        }
    }

    fun onArchiveToggle() {
        val id = editingId ?: return
        viewModelScope.launch {
            if (_state.value.isArchived) {
                original?.let { writing.update(it.copy(isArchived = false)) }
            } else {
                writing.archive(id)
            }
            _state.update { it.copy(isDone = true) }
        }
    }

    fun onDelete() {
        val id = editingId ?: return
        viewModelScope.launch {
            try {
                writing.delete(id)
                _state.update { it.copy(isDone = true) }
            } catch (e: DomainError.AccountHasTransactions) {
                // History appeared since the screen opened: archiving is the alternative (FR-203).
                _state.update { it.copy(canDelete = false, openingLocked = true) }
            }
        }
    }

    private companion object {
        const val NAME_MAX = 40
        val NAME_ISSUES = setOf(AccountEditorIssue.NAME_REQUIRED, AccountEditorIssue.NAME_TOO_LONG)
    }
}
