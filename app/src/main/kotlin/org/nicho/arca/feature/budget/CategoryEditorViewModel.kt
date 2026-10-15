package org.nicho.arca.feature.budget

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.nicho.arca.domain.budget.BudgetService
import org.nicho.arca.domain.budget.BudgetStep
import org.nicho.arca.domain.error.DomainError
import org.nicho.arca.domain.error.PersistenceError
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.parsing.CurrencyParser
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.navigation.CategoryEditorRoute

/**
 * Category & envelope editor (SDD-ARC-002 §9.12 · FR-250–252, FR-260, FR-261, FR-265). Appearance
 * goes through CategoryStore; the budget and roll-over go through BudgetService, which applies a
 * change from the current month forward and keeps the history (FR-265).
 */
@HiltViewModel
class CategoryEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val categories: CategoryStore,
    private val budgets: BudgetService,
) : ViewModel() {

    private val editingId = savedStateHandle.toRoute<CategoryEditorRoute>().categoryId?.let { CategoryId(UUID.fromString(it)) }
    private var original: Category? = null

    private val _state = MutableStateFlow(CategoryEditorUiState(isLoading = editingId != null, isNew = editingId == null))
    val state: StateFlow<CategoryEditorUiState> = _state.asStateFlow()

    init {
        if (editingId != null) {
            viewModelScope.launch {
                val category = categories.byId(editingId) ?: run { _state.update { it.copy(isDone = true) }; return@launch }
                original = category
                _state.value = CategoryEditorUiState(
                    isLoading = false,
                    isNew = false,
                    name = category.name,
                    kind = category.kind,
                    icon = category.icon,
                    colorHex = category.colorHex,
                    budgetText = category.budgetMinor?.toString().orEmpty(),
                    rollsOver = category.rollsOver,
                    isSystemLocked = category.isSystemLocked,
                    isArchived = category.isArchived,
                )
            }
        }
    }

    fun onNameChange(name: String) = _state.update { it.copy(name = name, issues = it.issues - NAME_ISSUES) }

    /** New categories only: the kind is fixed once the category exists (DD-A21). */
    fun onKindChange(kind: CategoryKind) = _state.update { if (it.isNew) it.copy(kind = kind) else it }

    fun onIconChange(icon: String) = _state.update { it.copy(icon = icon) }

    fun onColorChange(hex: String) = _state.update { it.copy(colorHex = hex) }

    fun onBudgetChange(text: String) = _state.update { it.copy(budgetText = text, issues = it.issues - CategoryEditorIssue.BUDGET_UNCLEAR) }

    fun onRemoveBudget() = _state.update { it.copy(budgetText = "", rollsOver = false) }

    /** "Set a budget": opens the envelope at Rp500,000, ready for the stepper. */
    fun onSetBudget() = _state.update { it.copy(budgetText = BudgetStep.DEFAULT_MINOR.toString(), issues = it.issues - CategoryEditorIssue.BUDGET_UNCLEAR) }

    /** The − / + stepper, Rp50,000 a step; the domain does the arithmetic. */
    fun onBudgetStep(up: Boolean) = _state.update {
        val current = CurrencyParser.parseOrNull(it.budgetText)
        val next = if (up) BudgetStep.up(current) else BudgetStep.down(current)
        it.copy(budgetText = next.toString(), issues = it.issues - CategoryEditorIssue.BUDGET_UNCLEAR)
    }

    fun onRollsOverChange(rollsOver: Boolean) = _state.update { it.copy(rollsOver = rollsOver) }

    fun onSave() {
        val form = _state.value
        val name = form.name.trim()
        val issues = buildSet {
            if (name.isEmpty()) add(CategoryEditorIssue.NAME_REQUIRED)
            if (name.codePointCount(0, name.length) > NAME_MAX) add(CategoryEditorIssue.NAME_TOO_LONG)
        }
        val budget: Long? = if (!form.canBudget || form.budgetText.isBlank()) {
            null
        } else {
            CurrencyParser.parseOrNull(form.budgetText) ?: run {
                _state.update { it.copy(issues = issues + CategoryEditorIssue.BUDGET_UNCLEAR) }
                return
            }
        }
        if (issues.isNotEmpty()) {
            _state.update { it.copy(issues = issues) }
            return
        }
        viewModelScope.launch {
            try {
                val base = original
                val id = base?.id ?: CategoryId.random()
                if (base == null) {
                    categories.insert(
                        Category(
                            id = id,
                            name = name,
                            icon = form.icon,
                            colorHex = form.colorHex,
                            kind = form.kind,
                            isArchived = false,
                            isSystemLocked = false,
                            budgetMinor = null, // budgets go through BudgetService, below
                            rollsOver = false,
                        ),
                    )
                } else {
                    categories.update(base.copy(name = name, icon = form.icon, colorHex = form.colorHex))
                }
                if (form.canBudget) {
                    if (budget != base?.budgetMinor) budgets.setBudget(id, budget)
                    val rolls = form.rollsOver && budget != null
                    if (rolls != (base?.rollsOver ?: false)) budgets.setRollsOver(id, rolls)
                }
                _state.update { it.copy(isDone = true) }
            } catch (e: DomainError) {
                _state.update { it.copy(issues = it.issues + CategoryEditorIssue.SAVE_FAILED) }
            } catch (e: PersistenceError) {
                _state.update { it.copy(issues = it.issues + CategoryEditorIssue.SAVE_FAILED) }
            }
        }
    }

    fun onArchiveToggle() {
        val base = original ?: return
        viewModelScope.launch {
            if (base.isArchived) categories.unarchive(base.id) else categories.archive(base.id)
            _state.update { it.copy(isDone = true) }
        }
    }

    fun onDelete() {
        val base = original ?: return
        viewModelScope.launch {
            try {
                categories.delete(base.id)
                _state.update { it.copy(isDone = true) }
            } catch (e: DomainError.CategoryHasTransactions) {
                _state.update { it.copy(issues = it.issues + CategoryEditorIssue.IN_USE) }
            } catch (e: DomainError.CategoryIsSystemLocked) {
                _state.update { it.copy(issues = it.issues + CategoryEditorIssue.IN_USE) }
            }
        }
    }

    private companion object {
        const val NAME_MAX = 30
        val NAME_ISSUES = setOf(CategoryEditorIssue.NAME_REQUIRED, CategoryEditorIssue.NAME_TOO_LONG)
    }
}
