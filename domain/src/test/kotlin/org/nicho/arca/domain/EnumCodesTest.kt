package org.nicho.arca.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.nicho.arca.domain.model.AccountType
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.repository.ThemePreference

// SDD-ARC-002 §14.2 "Enum codes pinned". These strings are stored data and the wire contract
// shared with iOS and Firestore (§6.6). If one of these assertions fails, a rename just
// broke every database already on a phone.
class EnumCodesTest {

    @Test
    fun accountTypeCodes() {
        assertEquals(
            listOf("cash", "bank", "ewallet", "other"),
            AccountType.entries.map { it.code },
        )
    }

    @Test
    fun transactionTypeCodes() {
        assertEquals(
            listOf("income", "expense", "transferOut", "transferIn"),
            TransactionType.entries.map { it.code },
        )
    }

    @Test
    fun categoryKindCodes() {
        assertEquals(listOf("expense", "income"), CategoryKind.entries.map { it.code })
    }

    @Test
    fun themePreferenceCodes() {
        assertEquals(listOf("system", "light", "dark"), ThemePreference.entries.map { it.code })
    }

    @Test
    fun everyCodeRoundTrips() {
        AccountType.entries.forEach { assertEquals(it, AccountType.fromCode(it.code)) }
        TransactionType.entries.forEach { assertEquals(it, TransactionType.fromCode(it.code)) }
        CategoryKind.entries.forEach { assertEquals(it, CategoryKind.fromCode(it.code)) }
        ThemePreference.entries.forEach { assertEquals(it, ThemePreference.fromCode(it.code)) }
    }

    @Test
    fun unknownCodes_failOrFallBackAsDesigned() {
        assertEquals(AccountType.OTHER, AccountType.fromCode("crypto"))         // cosmetic: fall back
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromCode(null))
        assertThrows(NoSuchElementException::class.java) { TransactionType.fromCode("refund") } // would mis-sign a balance
        assertThrows(NoSuchElementException::class.java) { CategoryKind.fromCode("transfer") }
    }
}
