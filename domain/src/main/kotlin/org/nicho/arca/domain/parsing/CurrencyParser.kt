package org.nicho.arca.domain.parsing

/**
 * SDD-ARC-002 §7.5 · SDD-ARC-001 §6.4 · NFR-U05. The only parser for amounts that come from
 * outside: the capture form and the `amount` parameter of an inbound deep link.
 *
 * | Input                          | Result                     |
 * |--------------------------------|----------------------------|
 * | `50000` / `50.000` / `50,000`  | 50 000                     |
 * | `50rb` / `50k` / `50 rb`       | 50 000                     |
 * | `1jt` / `1,5jt` / `1.5jt`      | 1 000 000 / 1 500 000      |
 * | `Rp 50.000` / `Rp 50.000,-`    | 50 000                     |
 * | `-50000` / `0`                 | NOT_POSITIVE               |
 * | `1.5` (no suffix)              | AMBIGUOUS_SEPARATOR        |
 * | `50.00.0` / `abc`              | NOT_A_NUMBER               |
 *
 * DD-14: `.` and `,` are thousands separators unless a magnitude suffix follows, and then they
 * are decimal. The Rupiah has no subunit, so `50,000` can only mean fifty thousand. Don't "fix"
 * this to follow the locale's decimal separator: that would turn `50,000` into fifty.
 *
 * Built from the digit string, with checked arithmetic, so an overflow is TOO_LARGE and never
 * wraps. No Double anywhere (DD-12).
 */
object CurrencyParser {

    sealed interface Result {
        data class Amount(val minor: Long) : Result
        data class Invalid(val error: ParseError) : Result
    }

    enum class ParseError {
        EMPTY,
        NOT_A_NUMBER,
        NOT_POSITIVE,          // SDD-ARC-001 names this `.negative`; it covers 0 too
        TOO_LARGE,
        AMBIGUOUS_SEPARATOR,   // "Jumlah tidak jelas — coba 50000, 50rb, atau 1,5jt"
    }

    /** Suffix → number of zeros it adds. Matched case-insensitively. */
    private val SUFFIXES = listOf(
        "ribu" to 3, "rb" to 3, "k" to 3,
        "juta" to 6, "jt" to 6,
    )

    private const val MINUS_SIGNS = "-−"

    fun parse(input: String): Result {
        var text = input.trim()
        if (text.startsWith("rp", ignoreCase = true)) text = text.substring(2)
        text = text.filterNot { it.isWhitespace() } // "50 rb", "Rp 50.000"
        text = text.removeSuffix(",-").removeSuffix(".-") // "Rp 50.000,-", the usual receipt form
        if (text.isEmpty()) return Result.Invalid(ParseError.EMPTY)
        if (text[0] in MINUS_SIGNS) return Result.Invalid(ParseError.NOT_POSITIVE)

        val suffix = SUFFIXES.firstOrNull { (s, _) -> text.endsWith(s, ignoreCase = true) }
        val digits = if (suffix == null) {
            wholeNumber(text)
        } else {
            scaled(text.dropLast(suffix.first.length), zeros = suffix.second)
        }
        return when (digits) {
            is Digits.Ok -> toAmount(digits.value)
            is Digits.Bad -> Result.Invalid(digits.error)
        }
    }

    /**
     * An account's opening balance, which may be negative or zero (SRS §6.1: a credit card or an
     * overdraft opens below zero). Same grammar as [parse], plus a leading minus sign.
     */
    fun parseBalance(input: String): Result {
        val trimmed = input.trim()
        val negative = trimmed.startsWith('-') || trimmed.startsWith('−')
        val body = if (negative) trimmed.drop(1) else trimmed
        if (body.trimStart().firstOrNull() in MINUS_SIGNS.toList()) return Result.Invalid(ParseError.NOT_A_NUMBER) // "--5"
        return when (val parsed = parse(body)) {
            is Result.Amount -> if (negative) Result.Amount(Math.negateExact(parsed.minor)) else parsed
            // With the sign removed, NOT_POSITIVE can only mean the value is zero.
            is Result.Invalid -> if (parsed.error == ParseError.NOT_POSITIVE) Result.Amount(0) else parsed
        }
    }

    /** Convenience for callers that only need the value: null when [input] isn't a valid amount. */
    fun parseOrNull(input: String): Long? = (parse(input) as? Result.Amount)?.minor

    private sealed interface Digits {
        data class Ok(val value: String) : Digits
        data class Bad(val error: ParseError) : Digits
    }

    // No suffix: separators group thousands. "50.000" and "1,500,000" are fine; "1.5" looks like a
    // decimal, which the Rupiah can't have, so it's ambiguous rather than silently 15.
    private fun wholeNumber(text: String): Digits {
        val separators = text.filter { it == '.' || it == ',' }
        if (separators.isEmpty()) return digitsOnly(text)
        if (separators.toSet().size > 1) return Digits.Bad(ParseError.NOT_A_NUMBER) // "1.000,50"

        val groups = text.split(separators[0])
        if (groups.any { it.isEmpty() || !it.all(Char::isDigit) }) return Digits.Bad(ParseError.NOT_A_NUMBER)
        val tail = groups.drop(1)
        if (groups.size == 2 && tail[0].length in 1..2) return Digits.Bad(ParseError.AMBIGUOUS_SEPARATOR)
        if (groups[0].length > 3 || tail.any { it.length != 3 }) return Digits.Bad(ParseError.NOT_A_NUMBER)
        return Digits.Ok(groups.joinToString(""))
    }

    // With a suffix: at most one separator, and it's a decimal point. "1,5jt" → "1" + "500000".
    private fun scaled(number: String, zeros: Int): Digits {
        val separatorCount = number.count { it == '.' || it == ',' }
        if (separatorCount == 0) return digitsOnly(number).let { if (it is Digits.Ok) Digits.Ok(it.value + "0".repeat(zeros)) else it }
        if (separatorCount > 1) return Digits.Bad(ParseError.NOT_A_NUMBER)

        val (whole, fraction) = number.split('.', ',')
        if (whole.isEmpty() || fraction.isEmpty() || !(whole + fraction).all(Char::isDigit)) {
            return Digits.Bad(ParseError.NOT_A_NUMBER)
        }
        // "1.500rb": a 1.5 thousand, or a thousands-grouped 1500 thousand? Both readings are
        // plausible, so ask rather than guess. (Stricter than SDD-ARC-001 §6.4, which reads every
        // separator before a suffix as decimal.)
        if (fraction.length == 3) return Digits.Bad(ParseError.AMBIGUOUS_SEPARATOR)
        val significant = fraction.trimEnd('0')
        if (significant.length > zeros) return Digits.Bad(ParseError.NOT_A_NUMBER) // a fraction of a rupiah
        return Digits.Ok(whole + significant.padEnd(zeros, '0'))
    }

    private fun digitsOnly(text: String): Digits =
        if (text.isNotEmpty() && text.all(Char::isDigit)) Digits.Ok(text) else Digits.Bad(ParseError.NOT_A_NUMBER)

    private fun toAmount(digits: String): Result {
        var value = 0L
        try {
            for (c in digits) value = Math.addExact(Math.multiplyExact(value, 10L), (c - '0').toLong())
        } catch (e: ArithmeticException) {
            return Result.Invalid(ParseError.TOO_LARGE)
        }
        return if (value > 0) Result.Amount(value) else Result.Invalid(ParseError.NOT_POSITIVE)
    }
}
