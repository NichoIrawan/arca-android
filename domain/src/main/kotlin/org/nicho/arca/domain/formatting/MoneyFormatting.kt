package org.nicho.arca.domain.formatting

import org.nicho.arca.domain.model.TransactionType

/**
 * SDD-ARC-002 §7.7 · SDD-ARC-001 §5.3, DD-26. Every amount on screen goes through one of these;
 * a view never builds a money string itself.
 *
 * The number format is fixed: `.` groups thousands, `,` marks a decimal (only Compact shows one).
 * The currency is always IDR, so the display language changes labels, never the number. Nothing
 * here reads the device locale or uses NumberFormat, whose data differs between the JVM and the
 * phone, so a test that passes here passes on the device too.
 *
 * Built from the amount's digit string, not by dividing it: formatting does no money arithmetic.
 */
fun interface MoneyFormatting {
    fun format(amountMinor: Long): String
}

private const val MINUS = '−' // typographic minus, as the design system sets it

/** "Rp50.000", "−Rp50.000". Most screens. */
object StandardMoneyFormat : MoneyFormatting {
    override fun format(amountMinor: Long): String {
        val (negative, digits) = signAndDigits(amountMinor)
        return (if (negative) "$MINUS" else "") + "Rp" + groupThousands(digits)
    }
}

/**
 * "+Rp50.000", "−Rp50.000". Where the sign, not a colour, says which way money moved (NFR-A04).
 * DSG-ARC-001 §3.2: income +, expense −, transfer principal unsigned.
 */
object SignedMoneyFormat : MoneyFormatting {
    override fun format(amountMinor: Long): String {
        val (negative, digits) = signAndDigits(amountMinor)
        val sign = when {
            negative -> "$MINUS"
            digits == "0" -> ""
            else -> "+"
        }
        return sign + "Rp" + groupThousands(digits)
    }

    /** A transaction row. [amountMinor] is the stored, always-positive amount (DD-11). */
    fun format(amountMinor: Long, type: TransactionType): String {
        val plain = StandardMoneyFormat.format(amountMinor)
        return when (type) {
            TransactionType.INCOME -> "+$plain"
            TransactionType.EXPENSE -> "$MINUS$plain"
            TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN -> plain
        }
    }
}

/**
 * "Rp850", "Rp50 rb", "Rp1,2 jt", "Rp3,5 M". Widgets and other tight spaces (FR-141).
 * One decimal at most, cut rather than rounded, so a compact figure never shows more money than
 * there is.
 */
object CompactMoneyFormat : MoneyFormatting {
    private val UNITS = listOf(12 to "T", 9 to "M", 6 to "jt", 3 to "rb") // triliun, miliar, juta, ribu

    override fun format(amountMinor: Long): String {
        val (negative, digits) = signAndDigits(amountMinor)
        val sign = if (negative) "$MINUS" else ""
        val unit = UNITS.firstOrNull { (zeros, _) -> digits.length > zeros }
            ?: return sign + "Rp" + digits
        val (zeros, label) = unit
        val whole = digits.dropLast(zeros)
        val firstDecimal = digits[digits.length - zeros]
        val decimal = if (firstDecimal == '0') "" else ",$firstDecimal"
        return sign + "Rp" + groupThousands(whole) + decimal + " " + label
    }
}

/**
 * Words for a screen reader: "lima puluh ribu rupiah", "fifty thousand rupiah" (NFR-A01).
 * TalkBack reading "Rp50.000" digit by digit, or the dot as a decimal point, is what this exists
 * to prevent. Pick the language the app is showing.
 */
class AccessibleMoneyFormat(private val language: SpokenLanguage) : MoneyFormatting {

    enum class SpokenLanguage { INDONESIAN, ENGLISH }

    override fun format(amountMinor: Long): String {
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

/** "1234567" → "1.234.567". */
internal fun groupThousands(digits: String): String =
    digits.reversed().chunked(3).joinToString(".").reversed()

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
