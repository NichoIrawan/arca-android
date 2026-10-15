package org.nicho.arca.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.nicho.arca.domain.error.DomainError
import org.nicho.arca.domain.error.PersistenceError
import org.nicho.arca.domain.calculation.BalanceCalculator
import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.BalanceRow
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionDraft
import org.nicho.arca.domain.model.TransactionId
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.model.TransferGroupId
import org.nicho.arca.domain.parsing.CurrencyParser
import org.nicho.arca.domain.repository.AccountReading
import org.nicho.arca.domain.repository.CaptureContextStore
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.repository.TransactionReading
import org.nicho.arca.domain.repository.TransactionWriting
import org.nicho.arca.domain.transfer.TransferDraft
import org.nicho.arca.domain.transfer.TransferIssue
import org.nicho.arca.domain.transfer.TransferService
import org.nicho.arca.domain.validation.TransactionValidator
import org.nicho.arca.domain.validation.ValidationIssue

/**
 * The capture form (SDD-ARC-002 §9.2, DD-A12) for expense, income and transfer, new or edited.
 * The in-app sheet and CaptureActivity both use this, so the two entry points can't diverge.
 *
 * It parses and validates through :domain and never does arithmetic. It also carries no budget
 * dependency: recording stays free of judgment (FR-267).
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val accountsStore: AccountReading,
    private val categoriesStore: CategoryStore,
    private val reading: TransactionReading,
    private val writing: TransactionWriting,
    private val transfers: TransferService,
    private val captureContext: CaptureContextStore,
    private val validator: TransactionValidator,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private var allCategories: List<Category> = emptyList()

    /** Every account's balance now, for the "below zero after this" warning (2026-10-14). */
    private var latestBalances: Map<AccountId, Long> = emptyMap()
    private var editing: Editing? = null

    /** What's being edited: a single row, or a whole transfer group. */
    private data class Editing(val transaction: Transaction, val groupId: TransferGroupId?, val originalDate: Instant)

    init {
        viewModelScope.launch {
            combine(reading.observeBalanceRows(), accountsStore.observeAll(includeArchived = true)) { rows, accounts ->
                BalanceCalculator.balances(rows, accounts)
            }.collect { balances ->
                latestBalances = balances
                _state.update { it.copy(balances = balances) }
            }
        }
        viewModelScope.launch {
            combine(
                accountsStore.observeAll(includeArchived = false),
                categoriesStore.observeAll(includeArchived = false),
            ) { accounts, categories -> accounts to categories }.collect { (accounts, categories) ->
                allCategories = categories
                _state.update {
                    it.copy(
                        accounts = accounts.sortedByDescending(Account::isFavorite),
                        categories = categoriesFor(it.mode),
                    )
                }
            }
        }
    }

    /** Opens the form for [request]. Called each time the sheet or CaptureActivity opens. */
    fun start(request: CaptureRequest) {
        // Reset now, not inside the coroutine: this ViewModel outlives a sheet, and a stale
        // isDone from the previous save would close the new sheet the moment it opens.
        _state.value = CaptureUiState(balances = latestBalances, mode = request.mode, accounts = _state.value.accounts)
        viewModelScope.launch {
            editing = null
            val accounts = accountsStore.observeAll(includeArchived = false).first().sortedByDescending(Account::isFavorite)
            allCategories = categoriesStore.observeAll(includeArchived = false).first()
            val editId = request.editing
            if (editId != null) {
                startEditing(editId, accounts)
                return@launch
            }
            val remembered = captureContext.context.first().lastAccountId?.takeIf { id -> accounts.any { it.id == id } }
            val account = request.accountId?.takeIf { id -> accounts.any { it.id == id } } ?: remembered ?: accounts.firstOrNull()?.id
            val parsed = request.amountText?.let(CurrencyParser::parseOrNull)
            _state.value = CaptureUiState(
                balances = latestBalances,
                mode = request.mode,
                accounts = accounts,
                accountId = account,
                categories = categoriesFor(request.mode),
                categoryId = request.categoryId?.takeIf { id -> categoriesFor(request.mode).any { it.id == id } },
                destinationId = accounts.firstOrNull { it.id != account }?.id,
                amountText = request.amountText.orEmpty(),
                amountMinor = parsed,
                date = today(),
            )
        }
    }

    private suspend fun startEditing(id: TransactionId, accounts: List<Account>) {
        val tx = reading.byId(id) ?: run { _state.update { it.copy(isDone = true) }; return }
        var replaced: List<BalanceRow>
        val groupId = tx.transferGroupId
        if (groupId != null) {
            val members = transfers.members(groupId)
            val out = members.firstOrNull { it.type == TransactionType.TRANSFER_OUT } ?: tx
            val fee = members.firstOrNull { it.type == TransactionType.EXPENSE }
            editing = Editing(out, groupId, out.date)
            replaced = members.map { BalanceRow(it.accountId, it.amountMinor, it.type) }
            _state.value = CaptureUiState(
                balances = latestBalances,
                mode = CaptureMode.TRANSFER,
                isEditing = true,
                accounts = accounts,
                accountId = out.accountId,
                destinationId = out.counterAccountId,
                amountText = out.amountMinor.toString(),
                amountMinor = out.amountMinor,
                feeText = fee?.amountMinor?.toString().orEmpty(),
                date = localDate(out.date),
                note = out.note.orEmpty(),
            )
        } else {
            val mode = if (tx.type == TransactionType.INCOME) CaptureMode.INCOME else CaptureMode.EXPENSE
            editing = Editing(tx, null, tx.date)
            replaced = listOf(BalanceRow(tx.accountId, tx.amountMinor, tx.type))
            _state.value = CaptureUiState(
                balances = latestBalances,
                mode = mode,
                isEditing = true,
                accounts = accounts,
                accountId = tx.accountId,
                categories = categoriesFor(mode),
                categoryId = tx.categoryId,
                amountText = tx.amountMinor.toString(),
                amountMinor = tx.amountMinor,
                date = localDate(tx.date),
                note = tx.note.orEmpty(),
            )
        }
        _state.update { it.copy(replacedRows = replaced) }
    }

    // ── Field changes ──

    fun onModeChange(mode: CaptureMode) = _state.update {
        if (it.mode == mode) it else it.copy(
            mode = mode,
            categories = categoriesFor(mode),
            categoryId = null, // a category of the other kind can't carry over (DD-A21)
            issues = emptySet(),
        )
    }

    fun onAmountChange(text: String) = _state.update {
        it.copy(amountText = text, amountMinor = CurrencyParser.parseOrNull(text), issues = it.issues - AMOUNT_ISSUES)
    }

    /** The suffix chips above the keypad: "000", "rb"/"K", "jt" (SDD-ARC-002 §9.6). */
    fun onAppendSuffix(suffix: String) = onAmountChange(_state.value.amountText + suffix)

    fun onAccountChange(id: AccountId) = _state.update {
        it.copy(
            accountId = id,
            destinationId = if (it.destinationId == id) it.accounts.firstOrNull { a -> a.id != id }?.id else it.destinationId,
            issues = it.issues - setOf(CaptureIssue.ACCOUNT_REQUIRED, CaptureIssue.SAME_ACCOUNT),
        )
    }

    fun onDestinationChange(id: AccountId) = _state.update {
        it.copy(destinationId = id, issues = it.issues - setOf(CaptureIssue.DESTINATION_REQUIRED, CaptureIssue.SAME_ACCOUNT))
    }

    fun onCategoryChange(id: CategoryId) = _state.update {
        it.copy(categoryId = id, issues = it.issues - setOf(CaptureIssue.CATEGORY_REQUIRED, CaptureIssue.CATEGORY_MISMATCH))
    }

    fun onFeeChange(text: String) = _state.update { it.copy(feeText = text, issues = it.issues - CaptureIssue.FEE_INVALID) }

    fun onDateChange(date: LocalDate) = _state.update { it.copy(date = date, issues = it.issues - CaptureIssue.FUTURE_DATE) }

    fun onNoteChange(note: String) = _state.update { it.copy(note = note, issues = it.issues - CaptureIssue.NOTE_TOO_LONG) }

    // ── Save ──

    fun onSave() {
        val form = _state.value
        if (form.isSaving) return
        val parsed = CurrencyParser.parse(form.amountText)
        if (parsed is CurrencyParser.Result.Invalid) {
            _state.update { it.copy(issues = it.issues + CaptureUiState.issueFor(parsed.error)) }
            return
        }
        val amount = (parsed as CurrencyParser.Result.Amount).minor
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val issues = try {
                if (form.isTransfer) saveTransfer(form, amount) else saveSingle(form, amount)
            } catch (e: DomainError) {
                setOf(CaptureIssue.SAVE_FAILED)
            } catch (e: PersistenceError) {
                setOf(CaptureIssue.SAVE_FAILED)
            }
            if (issues.isEmpty()) {
                form.accountId?.let { captureContext.remember(it, instantFor(form.date)) } // FR-104
                _state.update { it.copy(isSaving = false, isDone = true) }
            } else {
                _state.update { it.copy(isSaving = false, issues = issues) }
            }
        }
    }

    private suspend fun saveSingle(form: CaptureUiState, amount: Long): Set<CaptureIssue> {
        val type = if (form.mode == CaptureMode.INCOME) TransactionType.INCOME else TransactionType.EXPENSE
        val date = instantFor(form.date)
        val draft = TransactionDraft(
            amountMinor = amount,
            type = type,
            date = date,
            account = form.account(form.accountId),
            category = allCategories.firstOrNull { it.id == form.categoryId },
            note = form.note.trim().ifEmpty { null },
            now = clock.instant(),
        )
        val issues = validator.validate(draft)
        if (issues.isNotEmpty()) return issues.map(::fromValidation).toSet()
        val existing = editing?.transaction
        val tx = Transaction(
            id = existing?.id ?: TransactionId.random(),
            amountMinor = amount,
            type = type,
            date = date,
            note = draft.note,
            accountId = checkNotNull(form.accountId),
            categoryId = form.categoryId,
            transferGroupId = null,
            counterAccountId = null,
        )
        if (existing != null) writing.update(tx) else writing.insert(tx)
        return emptySet()
    }

    private suspend fun saveTransfer(form: CaptureUiState, amount: Long): Set<CaptureIssue> {
        val fee = if (form.feeText.isBlank()) {
            null
        } else {
            CurrencyParser.parseOrNull(form.feeText) ?: return setOf(CaptureIssue.FEE_INVALID)
        }
        val draft = TransferDraft(
            source = form.account(form.accountId),
            destination = form.account(form.destinationId),
            amountMinor = amount,
            feeMinor = fee,
            date = instantFor(form.date),
            note = form.note.trim().ifEmpty { null },
            now = clock.instant(),
        )
        return try {
            val groupId = editing?.groupId
            if (groupId != null) transfers.updateTransfer(groupId, draft) else transfers.createTransfer(draft)
            emptySet()
        } catch (e: DomainError.TransferInvalid) {
            e.issues.map(::fromTransfer).toSet()
        }
    }

    // ── Helpers ──

    private fun categoriesFor(mode: CaptureMode): List<Category> = when (mode) {
        CaptureMode.EXPENSE -> allCategories.filter { it.kind == CategoryKind.EXPENSE }.sortedBy(Category::isSystemLocked) // Fees last
        CaptureMode.INCOME -> allCategories.filter { it.kind == CategoryKind.INCOME }
        CaptureMode.TRANSFER -> emptyList()
    }

    private fun zone(): ZoneId = ZoneId.systemDefault()

    private fun today(): LocalDate = clock.instant().atZone(zone()).toLocalDate()

    private fun localDate(instant: Instant): LocalDate = instant.atZone(zone()).toLocalDate()

    /**
     * The instant saved for a picked day: an unchanged day keeps the original time (edits);
     * today gets the current time; another day gets the current time of day on that day, so the
     * day's rows keep the order they were entered in.
     */
    private fun instantFor(date: LocalDate): Instant {
        val original = editing?.originalDate
        if (original != null && localDate(original) == date) return original
        if (date == today()) return clock.instant()
        return date.atTime(LocalTime.now(clock.withZone(zone()))).atZone(zone()).toInstant()
    }

    private fun fromValidation(issue: ValidationIssue): CaptureIssue = when (issue) {
        ValidationIssue.AMOUNT_NOT_POSITIVE -> CaptureIssue.AMOUNT_NOT_POSITIVE
        ValidationIssue.FUTURE_DATE -> CaptureIssue.FUTURE_DATE
        ValidationIssue.CATEGORY_REQUIRED -> CaptureIssue.CATEGORY_REQUIRED
        ValidationIssue.CATEGORY_KIND_MISMATCH -> CaptureIssue.CATEGORY_MISMATCH
        ValidationIssue.NOTE_TOO_LONG -> CaptureIssue.NOTE_TOO_LONG
        ValidationIssue.ACCOUNT_REQUIRED -> CaptureIssue.ACCOUNT_REQUIRED
        ValidationIssue.ACCOUNT_ARCHIVED -> CaptureIssue.ACCOUNT_ARCHIVED
    }

    private fun fromTransfer(issue: TransferIssue): CaptureIssue = when (issue) {
        TransferIssue.SOURCE_REQUIRED -> CaptureIssue.ACCOUNT_REQUIRED
        TransferIssue.DESTINATION_REQUIRED -> CaptureIssue.DESTINATION_REQUIRED
        TransferIssue.SAME_ACCOUNT -> CaptureIssue.SAME_ACCOUNT
        TransferIssue.AMOUNT_NOT_POSITIVE -> CaptureIssue.AMOUNT_NOT_POSITIVE
        TransferIssue.FEE_NOT_POSITIVE -> CaptureIssue.FEE_INVALID
        TransferIssue.FUTURE_DATE -> CaptureIssue.FUTURE_DATE
        TransferIssue.ARCHIVED_ACCOUNT -> CaptureIssue.ACCOUNT_ARCHIVED
        TransferIssue.NOTE_TOO_LONG -> CaptureIssue.NOTE_TOO_LONG
    }

    private companion object {
        val AMOUNT_ISSUES = setOf(
            CaptureIssue.AMOUNT_EMPTY,
            CaptureIssue.AMOUNT_UNCLEAR,
            CaptureIssue.AMOUNT_NOT_POSITIVE,
            CaptureIssue.AMOUNT_TOO_LARGE,
        )
    }
}
