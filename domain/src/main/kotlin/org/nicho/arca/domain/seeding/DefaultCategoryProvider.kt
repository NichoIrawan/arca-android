package org.nicho.arca.domain.seeding

import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.CategoryKind

// SDD-ARC-002 §8.7 · FR-250. Seeded names are user data, not string resources: switching the
// app language doesn't rename them (§9.11), and the user can rename them freely.
// Icons are Lucide names (DSG-ARC-001 §7.1). Colours are the light-theme values of the five
// category hues, assigned in order (DSG-ARC-001 §3.6); the UI maps each to its dark variant.
object DefaultCategoryProvider {

    const val CLAY = "#c56a3e"
    const val BLUE = "#3260a3"
    const val TEAL = "#069f89"
    const val PLUM = "#914a8c"
    const val OLIVE = "#8e9337"

    /** The five category hues, in assignment order. */
    val palette: List<String> = listOf(CLAY, BLUE, TEAL, PLUM, OLIVE)

    /** Fresh ids on every call. Seeding happens once, so the ids only need to be unique. */
    fun defaults(): List<Category> = listOf(
        expense("Makanan & Minuman", "utensils", CLAY),
        expense("Transportasi", "car", BLUE),
        expense("Belanja", "shopping-bag", TEAL),
        expense("Tagihan", "file-text", PLUM),
        expense("Kesehatan", "heart", OLIVE),
        expense("Hiburan", "film", CLAY),
        expense("Lainnya", "ellipsis", BLUE),
        expense("Biaya", "banknote", TEAL, isSystemLocked = true), // Fees (FR-133)
        income("Gaji", "briefcase", PLUM),
        income("Pemasukan Lainnya", "coins", OLIVE),
    )

    private fun expense(name: String, icon: String, colorHex: String, isSystemLocked: Boolean = false) =
        category(name, icon, colorHex, CategoryKind.EXPENSE, isSystemLocked)

    private fun income(name: String, icon: String, colorHex: String) =
        category(name, icon, colorHex, CategoryKind.INCOME, isSystemLocked = false)

    private fun category(
        name: String,
        icon: String,
        colorHex: String,
        kind: CategoryKind,
        isSystemLocked: Boolean,
    ) = Category(
        id = CategoryId.random(),
        name = name,
        icon = icon,
        colorHex = colorHex,
        kind = kind,
        isArchived = false,
        isSystemLocked = isSystemLocked,
        budgetMinor = null,  // budgets are self-authored; nothing starts budgeted (FR-266)
        rollsOver = false,
    )
}
