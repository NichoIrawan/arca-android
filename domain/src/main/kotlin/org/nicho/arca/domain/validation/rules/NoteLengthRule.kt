package org.nicho.arca.domain.validation.rules

import org.nicho.arca.domain.model.TransactionDraft
import org.nicho.arca.domain.validation.ValidationIssue
import org.nicho.arca.domain.validation.ValidationRule

// SRS §6.1: note ≤ 280 characters. Counted in code points, so an emoji is one character, not
// the two UTF-16 units String.length would count.
class NoteLengthRule(private val max: Int = MAX) : ValidationRule<TransactionDraft, ValidationIssue> {
    override fun check(draft: TransactionDraft): ValidationIssue? {
        val note = draft.note ?: return null
        return if (note.codePointCount(0, note.length) > max) ValidationIssue.NOTE_TOO_LONG else null
    }

    companion object {
        const val MAX = 280
    }
}
