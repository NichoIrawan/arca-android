package org.nicho.arca.domain.model

// SDD-ARC-002 §6.2, DD-A21. An unknown kind must not be guessed, so fromCode throws.
enum class CategoryKind(val code: String) {
    EXPENSE("expense"),
    INCOME("income");

    companion object {
        fun fromCode(code: String): CategoryKind = entries.first { it.code == code }
    }
}
