package org.nicho.arca.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import org.nicho.arca.domain.formatting.MoneyFormat

/**
 * Shows a plain run of digits grouped as the app language groups them ("50000" → "50,000" or
 * "50.000") while the field's own text stays exactly what was typed, suffixes and all. Anything
 * that isn't only digits ("50rb", "1,5jt") is shown as typed. Formatting only: no arithmetic.
 */
class GroupedDigits(private val money: MoneyFormat) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty() || raw.length > 15 || !raw.all(Char::isDigit) || raw.startsWith("0")) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val shown = money.digits(raw.toLong())
        // Maps a cursor position in the raw digits to the grouped text and back, by counting digits.
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var digits = 0
                shown.forEachIndexed { i, c ->
                    if (digits == offset) return i
                    if (c.isDigit()) digits++
                }
                return shown.length
            }

            override fun transformedToOriginal(offset: Int): Int = shown.take(offset).count(Char::isDigit)
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}
