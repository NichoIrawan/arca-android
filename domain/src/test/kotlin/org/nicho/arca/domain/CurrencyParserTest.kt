package org.nicho.arca.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.nicho.arca.domain.parsing.CurrencyParser
import org.nicho.arca.domain.parsing.CurrencyParser.ParseError
import org.nicho.arca.domain.parsing.CurrencyParser.Result

// SDD-ARC-001 §6.4 grammar table and §12.2 extras · NFR-U05.
class CurrencyParserTest {

    private fun assertAmount(expected: Long, input: String) =
        assertEquals("\"$input\"", Result.Amount(expected), CurrencyParser.parse(input))

    private fun assertError(expected: ParseError, input: String) =
        assertEquals("\"$input\"", Result.Invalid(expected), CurrencyParser.parse(input))

    @Test
    fun plainAndGroupedNumbers() {
        assertAmount(50_000, "50000")
        assertAmount(50_000, "50.000")
        assertAmount(50_000, "50,000") // DD-14: never fifty
        assertAmount(1_500_000, "1.500.000")
        assertAmount(1_500_000, "1,500,000")
    }

    @Test
    fun thousandSuffixes() {
        assertAmount(50_000, "50rb")
        assertAmount(50_000, "50k")
        assertAmount(50_000, "50K")
        assertAmount(50_000, "50 rb")
        assertAmount(150_000, "150ribu")
        assertAmount(1_500, "1,5rb")
    }

    @Test
    fun millionSuffixes() {
        assertAmount(1_000_000, "1jt")
        assertAmount(1_500_000, "1,5jt")
        assertAmount(1_500_000, "1.5jt")
        assertAmount(1_250_000, "1,25jt")
        assertAmount(2_000_000, "2 juta")
    }

    @Test
    fun currencyMarks() {
        assertAmount(50_000, "Rp 50.000")
        assertAmount(50_000, "rp50.000")
        assertAmount(50_000, "Rp50rb")
        assertAmount(50_000, "Rp 50.000,-")
        assertAmount(50_000, "  50000  ")
    }

    @Test
    fun notPositive() {
        assertError(ParseError.NOT_POSITIVE, "-50000")
        assertError(ParseError.NOT_POSITIVE, "−50.000")
        assertError(ParseError.NOT_POSITIVE, "0")
        assertError(ParseError.NOT_POSITIVE, "0rb")
    }

    @Test
    fun empty() {
        assertError(ParseError.EMPTY, "")
        assertError(ParseError.EMPTY, "   ")
        assertError(ParseError.EMPTY, "Rp")
    }

    @Test
    fun ambiguousSeparators() {
        assertError(ParseError.AMBIGUOUS_SEPARATOR, "1.5")   // DD-14: no suffix, looks like a decimal
        assertError(ParseError.AMBIGUOUS_SEPARATOR, "12,50")
        assertError(ParseError.AMBIGUOUS_SEPARATOR, "1.500rb") // 1,500 or 1,500,000?
    }

    @Test
    fun notANumber() {
        assertError(ParseError.NOT_A_NUMBER, "abc")
        assertError(ParseError.NOT_A_NUMBER, "50.00.0")
        assertError(ParseError.NOT_A_NUMBER, "1.000,50")  // mixed separators
        assertError(ParseError.NOT_A_NUMBER, "1,2345rb")  // a fraction of a rupiah
        assertError(ParseError.NOT_A_NUMBER, "rb")
        assertError(ParseError.NOT_A_NUMBER, "+50000")
        assertError(ParseError.NOT_A_NUMBER, "50.0000")
    }

    // SDD-ARC-002 §14.2: past Int.MAX_VALUE parses exactly; past Long.MAX_VALUE is TOO_LARGE.
    @Test
    fun largeValues() {
        assertAmount(3_000_000_000, "3.000.000.000")
        assertAmount(2_147_483_648, "2147483648")
        assertAmount(Long.MAX_VALUE, "9223372036854775807")
        assertError(ParseError.TOO_LARGE, "9223372036854775808")
        assertError(ParseError.TOO_LARGE, "99999999999999jt")
    }

    @Test
    fun parseOrNull() {
        assertEquals(50_000L, CurrencyParser.parseOrNull("50rb"))
        assertEquals(null, CurrencyParser.parseOrNull("abc"))
    }
}
