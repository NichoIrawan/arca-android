package org.nicho.arca.feature.budget

import org.nicho.arca.domain.model.CategoryKind

enum class CategoryEditorIssue { NAME_REQUIRED, NAME_TOO_LONG, BUDGET_UNCLEAR, IN_USE, SAVE_FAILED }

data class CategoryEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val icon: String = "tag",
    val colorHex: String = "#c56a3e",
    val budgetText: String = "",       // blank = not budgeted
    val rollsOver: Boolean = false,
    val isSystemLocked: Boolean = false,
    val isArchived: Boolean = false,
    val issues: Set<CategoryEditorIssue> = emptySet(),
    val isDone: Boolean = false,
) {
    /** Budget fields are absent, not disabled, for Fees and for income (SDD-ARC-002 §9.12). */
    val canBudget: Boolean get() = kind == CategoryKind.EXPENSE && !isSystemLocked
}
