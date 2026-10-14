package org.nicho.arca.domain.validation

import org.nicho.arca.domain.model.TransactionDraft
import org.nicho.arca.domain.validation.rules.ActiveAccountRule
import org.nicho.arca.domain.validation.rules.CategoryKindMatchesTypeRule
import org.nicho.arca.domain.validation.rules.CategoryPresenceRule
import org.nicho.arca.domain.validation.rules.NoteLengthRule
import org.nicho.arca.domain.validation.rules.NotFutureDatedRule
import org.nicho.arca.domain.validation.rules.PositiveAmountRule

/**
 * SDD-ARC-002 §7.6, DD-26. Validates an income or expense before it's saved.
 *
 * Returns every issue at once rather than stopping at the first, so the form can mark all
 * invalid fields together instead of costing the user a round trip per mistake (NFR-U01).
 */
class TransactionValidator(private val rules: List<ValidationRule<TransactionDraft, ValidationIssue>>) {

    fun validate(draft: TransactionDraft): List<ValidationIssue> = rules.mapNotNull { it.check(draft) }

    companion object {
        /** No budget rule, ever: FR-267 keeps budget state out of the capture flow. */
        val standard = TransactionValidator(
            listOf(
                PositiveAmountRule,
                NotFutureDatedRule,
                CategoryPresenceRule,
                CategoryKindMatchesTypeRule,
                NoteLengthRule(),
                ActiveAccountRule,
            )
        )
    }
}
