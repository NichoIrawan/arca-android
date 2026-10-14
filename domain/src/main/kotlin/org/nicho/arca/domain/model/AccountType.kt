package org.nicho.arca.domain.model

// SDD-ARC-002 §6.2, DD-A08. Persisted by `code`, never by name or ordinal. A released code is
// never renamed. An unknown code is cosmetic here, so it falls back to OTHER.
enum class AccountType(val code: String) {
    CASH("cash"),
    BANK("bank"),
    EWALLET("ewallet"),
    OTHER("other");

    companion object {
        fun fromCode(code: String): AccountType = entries.firstOrNull { it.code == code } ?: OTHER
    }
}
