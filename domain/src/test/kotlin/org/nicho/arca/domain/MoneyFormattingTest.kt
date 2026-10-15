package org.nicho.arca.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.nicho.arca.domain.formatting.AccessibleMoneyFormat
import org.nicho.arca.domain.formatting.AccessibleMoneyFormat.SpokenLanguage
import org.nicho.arca.domain.formatting.MoneyFormat
import org.nicho.arca.domain.formatting.NumberStyle
import org.nicho.arca.domain.model.TransactionType

// Expected strings are the Android prototype's (English) and their Indonesian equivalents.
class MoneyFormattingTest {

    private val minus = "−"
    private val en = MoneyFormat(NumberStyle.ENGLISH)
    private val id = MoneyFormat(NumberStyle.INDONESIAN)

    @Test
    fun amount() {
        assertEquals("Rp0", en.amount(0))
        assertEquals("Rp24,850,000", en.amount(24_850_000))
        assertEquals("Rp24.850.000", id.amount(24_850_000))
        assertEquals("${minus}Rp30.000", id.amount(-30_000))
        assertEquals("${minus}Rp9,223,372,036,854,775,808", en.amount(Long.MIN_VALUE))
    }

    @Test
    fun digitsWithoutCurrency() {
        assertEquals("14,500,000", en.digits(14_500_000))
        assertEquals("${minus}120,000", en.digits(-120_000))
        assertEquals("14.500.000", id.digits(14_500_000))
    }

    @Test
    fun signed() {
        assertEquals("+Rp172,500", en.signed(172_500))
        assertEquals("${minus}Rp50.000", id.signed(-50_000))
        assertEquals("Rp0", en.signed(0))
        assertEquals("+7,390,000", en.signed(7_390_000, withCurrency = false))
    }

    // DSG-ARC-001 §3.2: income +, expense −, transfer principal unsigned.
    @Test
    fun transactionRows() {
        assertEquals("+8,000,000", en.transaction(8_000_000, TransactionType.INCOME))
        assertEquals("${minus}32,000", en.transaction(32_000, TransactionType.EXPENSE))
        assertEquals("200,000", en.transaction(200_000, TransactionType.TRANSFER_OUT))
        assertEquals("200.000", id.transaction(200_000, TransactionType.TRANSFER_IN))
        assertEquals("${minus}Rp2,500", en.transaction(2_500, TransactionType.EXPENSE, withCurrency = true))
    }

    @Test
    fun compact_english() {
        assertEquals("Rp850", en.compact(850))
        assertEquals("Rp40K", en.compact(40_000))
        assertEquals("Rp965K", en.compact(965_999))
        assertEquals("Rp1.43M", en.compact(1_437_000)) // cut, not rounded up
        assertEquals("6.75M", en.compact(6_750_000, withCurrency = false))
        assertEquals("Rp3.5B", en.compact(3_500_000_000)) // trailing zeros dropped
        assertEquals("Rp1,250T", en.compact(1_250_000_000_000_000))
        assertEquals("${minus}Rp2M", en.compact(-2_000_000))
        assertEquals("Rp2.05M", en.compact(2_050_000))
    }

    @Test
    fun compact_indonesian() {
        assertEquals("Rp40 rb", id.compact(40_000))
        assertEquals("Rp1,43 jt", id.compact(1_437_000))
        assertEquals("Rp3,5 M", id.compact(3_500_000_000))
        assertEquals("Rp1 jt", id.compact(1_000_000))
    }

    @Test
    fun compactSigned() {
        assertEquals("+8.20M", en.compactSigned(8_200_000))
        assertEquals("${minus}5.35M", en.compactSigned(-5_350_000))
        assertEquals("+2,85 jt", id.compactSigned(2_850_000))
        assertEquals("+8.00M", en.compactSigned(8_000_000)) // fixed decimals, so the hero's figures line up
    }

    @Test
    fun ofLanguageTag() {
        assertEquals(NumberStyle.INDONESIAN, MoneyFormat.of("in").style)
        assertEquals(NumberStyle.INDONESIAN, MoneyFormat.of("id").style)
        assertEquals(NumberStyle.ENGLISH, MoneyFormat.of("en").style)
    }

    @Test
    fun accessible_indonesian() {
        val words = AccessibleMoneyFormat(SpokenLanguage.INDONESIAN)
        assertEquals("nol rupiah", words.format(0))
        assertEquals("lima puluh ribu rupiah", words.format(50_000))
        assertEquals("seribu rupiah", words.format(1_000))
        assertEquals("seratus sebelas rupiah", words.format(111))
        assertEquals("dua belas ribu lima ratus rupiah", words.format(12_500))
        assertEquals("satu juta lima ratus ribu rupiah", words.format(1_500_000))
        assertEquals("sepuluh juta seribu rupiah", words.format(10_001_000))
        assertEquals("tiga miliar rupiah", words.format(3_000_000_000))
        assertEquals("minus tiga puluh ribu rupiah", words.format(-30_000))
    }

    @Test
    fun accessible_english() {
        val words = AccessibleMoneyFormat(SpokenLanguage.ENGLISH)
        assertEquals("zero rupiah", words.format(0))
        assertEquals("fifty thousand rupiah", words.format(50_000))
        assertEquals("one hundred twelve thousand five hundred rupiah", words.format(112_500))
        assertEquals("one million twenty-one rupiah", words.format(1_000_021))
        assertEquals("minus fifteen rupiah", words.format(-15))
    }
}
