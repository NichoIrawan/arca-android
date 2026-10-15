package org.nicho.arca.feature.capture

import android.net.Uri
import java.util.UUID
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.TransactionId

enum class CaptureMode { EXPENSE, INCOME, TRANSFER }

/**
 * What an entry point pre-fills in the capture form (SDD-ARC-002 §10.3, DD-A13). The FAB, a
 * widget, a shortcut and a deep link all produce one of these. None of them can write anything:
 * the user always taps Save.
 *
 * [editing] opens the form on an existing transaction (or the whole transfer it belongs to).
 */
data class CaptureRequest(
    val mode: CaptureMode = CaptureMode.EXPENSE,
    val accountId: AccountId? = null,
    val categoryId: CategoryId? = null,
    val amountText: String? = null,
    val editing: TransactionId? = null,
    val token: Long = System.nanoTime(), // a fresh request every time, even with equal fields
) {
    companion object {
        /**
         * Reads `arca://capture?type=expense&account=<uuid>&category=<uuid>&amount=50rb`.
         * Every parameter is untrusted: an unknown type falls back to expense, a malformed id is
         * dropped, and the amount stays text for CurrencyParser to judge.
         */
        fun fromUri(uri: Uri?): CaptureRequest {
            if (uri == null || uri.scheme != "arca" || uri.host != "capture") return CaptureRequest()
            val mode = when (uri.getQueryParameter("type")?.lowercase()) {
                "income" -> CaptureMode.INCOME
                "transfer" -> CaptureMode.TRANSFER
                else -> CaptureMode.EXPENSE
            }
            return CaptureRequest(
                mode = mode,
                accountId = uri.getQueryParameter("account")?.toUuidOrNull()?.let(::AccountId),
                categoryId = uri.getQueryParameter("category")?.toUuidOrNull()?.let(::CategoryId),
                amountText = uri.getQueryParameter("amount")?.take(MAX_AMOUNT_LENGTH),
            )
        }

        private const val MAX_AMOUNT_LENGTH = 32

        private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
    }
}
