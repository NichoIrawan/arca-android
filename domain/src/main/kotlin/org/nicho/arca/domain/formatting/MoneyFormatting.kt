package org.nicho.arca.domain.formatting

import org.nicho.arca.domain.model.TransactionType

/**
 * How numbers are written in the language the app is showing. The currency is always IDR; the
 * language decides the separators and the compact units, as the Android prototype shows.
 *
 * This replaces SDD-ARC-002 §7.7's single fixed format (client decision, 2026-10-14): "50.000"
 * reads as fifty to an English reader. Nothing here reads the device locale or uses NumberFormat,
 * so a test on the JVM behaves exactly as the phone does.
 */
enum class NumberStyle(
    internal val grouping: Char,
    internal val decimal: Char,
    internal val thousand: String,
    internal val million: String,
    internal val billion: String,
    internal val trillion: String,
) {
    /** "Rp24.850.000", "Rp1,43 jt", "Rp40 rb". */
    INDONESIAN('.', ',', " rb", " jt", " M", " T"),

    /** "Rp24,850,000", "Rp1.43M", "Rp40K". */
    ENGLISH(',', '.', "K", "M", "B", "T"),
}

/**
 * SDD-ARC-002 §7.7, DD-26. Every amount on screen goes through here; a view never builds a money
 * string itself. Built from the amount's digit string, never by dividing it, so formatting does
 * no money arithmetic.
 *
 * Sign rules (DSG-ARC-001 §3.2): income "+", expense "−" (typographic minus), transfer unsigned.
 */
class MoneyFormat(val style: NumberStyle) {

    /** "Rp50,000", "−Rp30,000". */
    fun amount(amountMinor: Long): String {
        val (negative, digits) = signAndDigits(amountMinor)
        return (if (negative) MINUS else "") + CURRENCY + group(digits)
    }

    /** "50,000", "−30,000": for places that show "Rp" separately or not at all (list rows, heroes). */
    fun digits(amountMinor: Long): String {
        val (negative, digits) = signAndDigits(amountMinor)
        return (if (negative) MINUS else "") + group(digits)
    }

    /**
     * "30,000" for both 30,000 and −30,000: the size of an amount whose direction a label already
     * says ("Over by", "Out"). Drops the sign from the digit string; no negation.
     */
    fun magnitude(amountMinor: Long): String = group(signAndDigits(amountMinor).second)

    /** "+Rp50,000", "−Rp50,000", "Rp0": a signed value such as a net change. */
    fun signed(amountMinor: Long, withCurrency: Boolean = true): String {
        val (negative, digits) = signAndDigits(amountMinor)
        return signOf(negative, digits) + (if (withCurrency) CURRENCY else "") + group(digits)
    }

    /**
     * A transaction row, from the stored, always-positive amount (DD-11): "−32,000" for an
     * expense, "+8,000,000" for income, "200,000" for a transfer leg.
     */
    fun transaction(amountMinor: Long, type: TransactionType, withCurrency: Boolean = false): String {
        val plain = (if (withCurrency) CURRENCY else "") + group(signAndDigits(amountMinor).second)
        return when (type) {
            TransactionType.INCOME -> "+$plain"
            TransactionType.EXPENSE -> "$MINUS$plain"
            TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN -> plain
            TransactionType.ADJUSTMENT_IN -> "+$plain"
            TransactionType.ADJUSTMENT_OUT -> "$MINUS$plain"
        }
    }

    /**
     * "Rp1.43M", "Rp2.5M", "Rp1M", "Rp40K", "Rp850" (English) or "Rp1,43 jt", "Rp40 rb"
     * (Indonesian), for tight spaces. Thousands show no decimals; millions and up show up to two,
     * without trailing zeros, as the prototype prints them. Digits are cut, never rounded up, so a
     * compact figure never shows more money than there is.
     */
    fun compact(amountMinor: Long, withCurrency: Boolean = true): String {
        val (negative, digits) = signAndDigits(amountMinor)
        return (if (negative) MINUS else "") + (if (withCurrency) CURRENCY else "") + compactDigits(digits, trimZeros = true)
    }

    /**
     * "+8.20M", "−5.35M": a compact signed change (the Overview hero's In/Out/Net). Always two
     * decimals, so the three figures side by side line up.
     */
    fun compactSigned(amountMinor: Long, withCurrency: Boolean = false): String {
        val (negative, digits) = signAndDigits(amountMinor)
        return signOf(negative, digits) + (if (withCurrency) CURRENCY else "") + compactDigits(digits, trimZeros = false)
    }

    private fun compactDigits(digits: String, trimZeros: Boolean): String {
        val units = listOf(12 to style.trillion, 9 to style.billion, 6 to style.million, 3 to style.thousand)
        val (zeros, label) = units.firstOrNull { (z, _) -> digits.length > z } ?: return digits
        val whole = group(digits.dropLast(zeros))
        if (zeros == 3) return whole + label
        val decimals = digits.substring(digits.length - zeros, digits.length - zeros + 2)
            .let { if (trimZeros) it.trimEnd('0') else it }
        return if (decimals.isEmpty()) whole + label else whole + style.decimal + decimals + label
    }

    private fun group(digits: String): String =
        digits.reversed().chunked(3).joinToString(style.grouping.toString()).reversed()

    private fun signOf(negative: Boolean, digits: String) = when {
        negative -> MINUS
        digits == "0" -> ""
        else -> "+"
    }

    companion object {
        const val CURRENCY = "Rp"
        const val MINUS = "−" // typographic minus, as the design system sets it

        fun of(language: String): MoneyFormat =
            MoneyFormat(if (language == "in" || language == "id") NumberStyle.INDONESIAN else NumberStyle.ENGLISH)
    }
}

/**
 * Words for a screen reader: "lima puluh ribu rupiah", "fifty thousand rupiah" (NFR-A01).
 * TalkBack reading "Rp50.000" digit by digit, or the dot as a decimal point, is what this exists
 * to prevent. Pick the language the app is showing.
 */
class AccessibleMoneyFormat(private val language: SpokenLanguage) {

    enum class SpokenLanguage { INDONESIAN, ENGLISH }

    fun format(amountMinor: Long): String {
        val (negative, digits) = signAndDigits(amountMinor)
        val words = when (language) {
            SpokenLanguage.INDONESIAN -> IndonesianWords.of(digits)
            SpokenLanguage.ENGLISH -> EnglishWords.of(digits)
        }
        return (if (negative) "minus " else "") + words + " rupiah"
    }
}

// ── Digit-string helpers ──

/** Long.toString gives the digits of Long.MIN_VALUE too, which negating would overflow. */
private fun signAndDigits(amountMinor: Long): Pair<Boolean, String> {
    val text = amountMinor.toString()
    return if (text.startsWith('-')) true to text.drop(1) else false to text
}

/** "1234567" → ["001", "234", "567"]: three-digit groups, most significant first. */
private fun threeDigitGroups(digits: String): List<String> {
    val padded = digits.padStart((digits.length + 2) / 3 * 3, '0')
    return padded.chunked(3)
}

private fun Char.digit(): Int = this - '0'

private object IndonesianWords {
    private val ONES = listOf("nol", "satu", "dua", "tiga", "empat", "lima", "enam", "tujuh", "delapan", "sembilan")
    private val SCALES = listOf("", "ribu", "juta", "miliar", "triliun", "kuadriliun", "kuintiliun")

    fun of(digits: String): String {
        if (digits.all { it == '0' }) return "nol"
        val groups = threeDigitGroups(digits)
        return groups.mapIndexedNotNull { i, group ->
            if (group == "000") return@mapIndexedNotNull null
            val scale = SCALES[groups.size - 1 - i]
            when {
                scale == "ribu" && group == "001" -> "seribu" // not "satu ribu"
                scale.isEmpty() -> belowThousand(group)
                else -> belowThousand(group) + " " + scale
            }
        }.joinToString(" ")
    }

    private fun belowThousand(group: String): String {
        val (h, t, o) = group.map { it.digit() }
        val hundreds = when (h) {
            0 -> null
            1 -> "seratus"
            else -> "${ONES[h]} ratus"
        }
        val rest = when {
            t == 0 && o == 0 -> null
            t == 0 -> ONES[o]
            t == 1 && o == 0 -> "sepuluh"
            t == 1 && o == 1 -> "sebelas"
            t == 1 -> "${ONES[o]} belas"
            o == 0 -> "${ONES[t]} puluh"
            else -> "${ONES[t]} puluh ${ONES[o]}"
        }
        return listOfNotNull(hundreds, rest).joinToString(" ")
    }
}

private object EnglishWords {
    private val ONES = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
    private val SCALES = listOf("", "thousand", "million", "billion", "trillion", "quadrillion", "quintillion")

    fun of(digits: String): String {
        if (digits.all { it == '0' }) return "zero"
        val groups = threeDigitGroups(digits)
        return groups.mapIndexedNotNull { i, group ->
            if (group == "000") return@mapIndexedNotNull null
            val scale = SCALES[groups.size - 1 - i]
            if (scale.isEmpty()) belowThousand(group) else belowThousand(group) + " " + scale
        }.joinToString(" ")
    }

    private fun belowThousand(group: String): String {
        val (h, t, o) = group.map { it.digit() }
        val hundreds = if (h == 0) null else "${ONES[h]} hundred"
        val rest = when {
            t == 0 && o == 0 -> null
            t <= 1 -> ONES[t * 10 + o]
            o == 0 -> TENS[t]
            else -> "${TENS[t]}-${ONES[o]}"
        }
        return listOfNotNull(hundreds, rest).joinToString(" ")
    }
}
