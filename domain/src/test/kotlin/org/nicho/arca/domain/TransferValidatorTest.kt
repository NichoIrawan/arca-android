package org.nicho.arca.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.nicho.arca.domain.transfer.TransferDraft
import org.nicho.arca.domain.transfer.TransferIssue
import org.nicho.arca.domain.transfer.TransferValidator
import org.nicho.arca.testing.testAccount

// DD-24: pure, so no store is needed.
class TransferValidatorTest {

    private val bca = testAccount("BCA")
    private val gopay = testAccount("GoPay")
    private val valid = TransferDraft(
        source = bca,
        destination = gopay,
        amountMinor = 500_000,
        feeMinor = 2_500,
        date = NOW,
        note = null,
        now = NOW,
    )

    private fun issues(draft: TransferDraft) = TransferValidator.validate(draft)

    @Test
    fun aValidTransfer_withOrWithoutFee() {
        assertEquals(emptyList<TransferIssue>(), issues(valid))
        assertEquals(emptyList<TransferIssue>(), issues(valid.copy(feeMinor = null)))
    }

    @Test
    fun sameAccount_isRejected() {
        assertEquals(listOf(TransferIssue.SAME_ACCOUNT), issues(valid.copy(destination = bca)))
    }

    @Test
    fun amountsMustBePositive() {
        assertEquals(listOf(TransferIssue.AMOUNT_NOT_POSITIVE), issues(valid.copy(amountMinor = 0)))
        assertEquals(listOf(TransferIssue.FEE_NOT_POSITIVE), issues(valid.copy(feeMinor = 0)))
    }

    @Test
    fun futureDate_andArchivedAccounts_areRejected() {
        assertEquals(listOf(TransferIssue.FUTURE_DATE), issues(valid.copy(date = NOW.plusSeconds(1))))
        assertEquals(
            listOf(TransferIssue.ARCHIVED_ACCOUNT),
            issues(valid.copy(destination = testAccount("Old", isArchived = true))),
        )
    }

    @Test
    fun missingAccounts_areReported_withEverythingElse() {
        assertEquals(
            listOf(TransferIssue.SOURCE_REQUIRED, TransferIssue.DESTINATION_REQUIRED, TransferIssue.AMOUNT_NOT_POSITIVE),
            issues(valid.copy(source = null, destination = null, amountMinor = 0)),
        )
    }
}
