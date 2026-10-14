package org.nicho.arca.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.nicho.arca.domain.formatting.AccessibleMoneyFormat
import org.nicho.arca.domain.formatting.AccessibleMoneyFormat.SpokenLanguage
import org.nicho.arca.domain.formatting.CompactMoneyFormat
import org.nicho.arca.domain.formatting.SignedMoneyFormat
import org.nicho.arca.domain.formatting.StandardMoneyFormat
import org.nicho.arca.domain.model.TransactionType

// SDD-ARC-002 §7.7. These run on the JVM and pass on the phone too, because nothing reads the
// device locale.
class MoneyFormattingTest {

    private val minus = "−"

    @Test
    fun standard() {
        assertEquals("Rp0", StandardMoneyFormat.format(0))
        assertEquals("Rp500", StandardMoneyFormat.format(500))
        assertEquals("Rp50.000", StandardMoneyFormat.format(50_000))
        assertEquals("Rp1.234.567", StandardMoneyFormat.format(1_234_567))
        assertEquals("${minus}Rp30.000", StandardMoneyFormat.format(-30_000))
        assertEquals("${minus}Rp9.223.372.036.854.775.808", StandardMoneyFormat.format(Long.MIN_VALUE))
    }

    @Test
    fun signed() {
        assertEquals("+Rp50.000", SignedMoneyFormat.format(50_000))
        assertEquals("${minus}Rp50.000", SignedMoneyFormat.format(-50_000))
        assertEquals("Rp0", SignedMoneyFormat.format(0))
    }

    // DSG-ARC-001 §3.2: income +, expense −, transfer principal unsigned.
    @Test
    fun signedByTransactionType() {
        assertEquals("+Rp15.000.000", SignedMoneyFormat.format(15_000_000, TransactionType.INCOME))
        assertEquals("${minus}Rp50.000", SignedMoneyFormat.format(50_000, TransactionType.EXPENSE))
        assertEquals("Rp500.000", SignedMoneyFormat.format(500_000, TransactionType.TRANSFER_OUT))
        assertEquals("Rp500.000", SignedMoneyFormat.format(500_000, TransactionType.TRANSFER_IN))
    }

    @Test
    fun compact() {
        assertEquals("Rp850", CompactMoneyFormat.format(850))
        assertEquals("Rp1 rb", CompactMoneyFormat.format(1_000))
        assertEquals("Rp50 rb", CompactMoneyFormat.format(50_000))
        assertEquals("Rp12,5 rb", CompactMoneyFormat.format(12_500))
        assertEquals("Rp1,2 jt", CompactMoneyFormat.format(1_250_000)) // cut, not rounded up
        assertEquals("Rp999,9 rb", CompactMoneyFormat.format(999_999))
        assertEquals("Rp3,5 M", CompactMoneyFormat.format(3_500_000_000))
        assertEquals("Rp1.250 T", CompactMoneyFormat.format(1_250_000_000_000_000))
        assertEquals("${minus}Rp2 jt", CompactMoneyFormat.format(-2_000_000))
    }

    @Test
    fun accessible_indonesian() {
        val id = AccessibleMoneyFormat(SpokenLanguage.INDONESIAN)
        assertEquals("nol rupiah", id.format(0))
        assertEquals("lima puluh ribu rupiah", id.format(50_000))
        assertEquals("seribu rupiah", id.format(1_000))
        assertEquals("seratus sebelas rupiah", id.format(111))
        assertEquals("dua belas ribu lima ratus rupiah", id.format(12_500))
        assertEquals("satu juta lima ratus ribu rupiah", id.format(1_500_000))
        assertEquals("sepuluh juta seribu rupiah", id.format(10_001_000))
        assertEquals("tiga miliar rupiah", id.format(3_000_000_000))
        assertEquals("minus tiga puluh ribu rupiah", id.format(-30_000))
    }

    @Test
    fun accessible_english() {
        val en = AccessibleMoneyFormat(SpokenLanguage.ENGLISH)
        assertEquals("zero rupiah", en.format(0))
        assertEquals("fifty thousand rupiah", en.format(50_000))
        assertEquals("one hundred twelve thousand five hundred rupiah", en.format(112_500))
        assertEquals("one million twenty-one rupiah", en.format(1_000_021))
        assertEquals("minus fifteen rupiah", en.format(-15))
    }
}
