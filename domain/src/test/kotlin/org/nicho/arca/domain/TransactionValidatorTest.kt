package org.nicho.arca.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nicho.arca.domain.model.CategoryKind
import org.nicho.arca.domain.model.TransactionDraft
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.validation.TransactionValidator
import org.nicho.arca.domain.validation.ValidationIssue
import org.nicho.arca.domain.validation.rules.ActiveAccountRule
import org.nicho.arca.domain.validation.rules.CategoryKindMatchesTypeRule
import org.nicho.arca.domain.validation.rules.CategoryPresenceRule
import org.nicho.arca.domain.validation.rules.NoteLengthRule
import org.nicho.arca.domain.validation.rules.NotFutureDatedRule
import org.nicho.arca.domain.validation.rules.PositiveAmountRule
import org.nicho.arca.testing.testAccount
import org.nicho.arca.testing.testCategory

// DD-26: each rule tested on its own, then the standard set together.
class TransactionValidatorTest {

    private val food = testCategory(kind = CategoryKind.EXPENSE)
    private val salary = testCategory("Gaji", kind = CategoryKind.INCOME)
    private val valid = TransactionDraft(
        amountMinor = 50_000,
        type = TransactionType.EXPENSE,
        date = NOW.minusSeconds(60),
        account = testAccount(),
        category = food,
        note = null,
        now = NOW,
    )

    @Test
    fun aValidDraft_hasNoIssues() {
        assertEquals(emptyList<ValidationIssue>(), TransactionValidator.standard.validate(valid))
    }

    @Test
    fun positiveAmountRule() {
        assertNull(PositiveAmountRule.check(valid))
        assertEquals(ValidationIssue.AMOUNT_NOT_POSITIVE, PositiveAmountRule.check(valid.copy(amountMinor = 0)))
        assertEquals(ValidationIssue.AMOUNT_NOT_POSITIVE, PositiveAmountRule.check(valid.copy(amountMinor = -1)))
    }

    @Test
    fun notFutureDatedRule_usesTheDraftsNow() {
        assertNull(NotFutureDatedRule.check(valid.copy(date = NOW)))
        assertNull(NotFutureDatedRule.check(valid.copy(date = at(2020, 1, 1)))) // any past date (FR-103)
        assertEquals(ValidationIssue.FUTURE_DATE, NotFutureDatedRule.check(valid.copy(date = NOW.plusSeconds(1))))
    }

    @Test
    fun categoryPresenceRule() {
        assertEquals(ValidationIssue.CATEGORY_REQUIRED, CategoryPresenceRule.check(valid.copy(category = null)))
        assertEquals(
            ValidationIssue.CATEGORY_REQUIRED,
            CategoryPresenceRule.check(valid.copy(type = TransactionType.INCOME, category = null)),
        )
        assertNull(CategoryPresenceRule.check(valid.copy(type = TransactionType.TRANSFER_OUT, category = null)))
    }

    @Test
    fun categoryKindMatchesTypeRule() {
        assertNull(CategoryKindMatchesTypeRule.check(valid))
        assertNull(CategoryKindMatchesTypeRule.check(valid.copy(type = TransactionType.INCOME, category = salary)))
        assertEquals(ValidationIssue.CATEGORY_KIND_MISMATCH, CategoryKindMatchesTypeRule.check(valid.copy(category = salary)))
        assertEquals(
            ValidationIssue.CATEGORY_KIND_MISMATCH,
            CategoryKindMatchesTypeRule.check(valid.copy(type = TransactionType.INCOME, category = food)),
        )
        assertNull(CategoryKindMatchesTypeRule.check(valid.copy(category = null))) // CategoryPresenceRule's job
    }

    @Test
    fun noteLengthRule_countsCharactersNotUtf16Units() {
        val rule = NoteLengthRule()
        assertNull(rule.check(valid.copy(note = "a".repeat(280))))
        assertEquals(ValidationIssue.NOTE_TOO_LONG, rule.check(valid.copy(note = "a".repeat(281))))
        assertNull(rule.check(valid.copy(note = "🍜".repeat(280)))) // 560 UTF-16 units, 280 characters
    }

    @Test
    fun activeAccountRule() {
        assertEquals(ValidationIssue.ACCOUNT_REQUIRED, ActiveAccountRule.check(valid.copy(account = null)))
        assertEquals(
            ValidationIssue.ACCOUNT_ARCHIVED,
            ActiveAccountRule.check(valid.copy(account = testAccount(isArchived = true))),
        )
    }

    // Every issue at once, so the form can mark every bad field in one pass (NFR-U01).
    @Test
    fun theStandardSet_reportsEveryIssueAtOnce() {
        val bad = valid.copy(amountMinor = 0, date = NOW.plusSeconds(60), account = null, category = null)
        assertEquals(
            setOf(
                ValidationIssue.AMOUNT_NOT_POSITIVE,
                ValidationIssue.FUTURE_DATE,
                ValidationIssue.CATEGORY_REQUIRED,
                ValidationIssue.ACCOUNT_REQUIRED,
            ),
            TransactionValidator.standard.validate(bad).toSet(),
        )
    }
}
