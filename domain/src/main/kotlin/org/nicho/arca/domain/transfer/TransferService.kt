package org.nicho.arca.domain.transfer

import org.nicho.arca.domain.error.DomainError
import org.nicho.arca.domain.model.Account
import org.nicho.arca.domain.model.Category
import org.nicho.arca.domain.model.Transaction
import org.nicho.arca.domain.model.TransactionId
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.domain.model.TransferGroupId
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.repository.TransferPersisting

/**
 * SDD-ARC-002 §7.4, DD-06 · FR-130–133. The only type that builds a Transaction with a
 * transferGroupId, and the only way a transfer is written, edited or deleted. A transfer is
 * 2 rows (out at the source, in at the destination) or 3 with a fee: an ordinary expense at the
 * source, in the Fees category, sharing the group id.
 *
 * Every path validates first and builds all rows in memory, then writes them in one atomic call.
 * A failure leaves nothing half-written: no orphaned leg, no fee without its transfer.
 */
class TransferService(
    private val persistence: TransferPersisting,
    private val categories: CategoryStore,
) {

    /** FR-130. Throws DomainError.TransferInvalid or FeesCategoryMissing before writing anything. */
    suspend fun createTransfer(draft: TransferDraft): TransferGroupId {
        val plan = plan(draft)
        val groupId = TransferGroupId.random()
        persistence.insertAtomic(members(groupId, plan, existing = emptyList()))
        return groupId
    }

    /**
     * Edits a whole group: changing the amount, accounts or fee rewrites all members together, so
     * the two legs can never disagree. Existing rows keep their ids; a fee added by the edit gets
     * a new one, and a removed fee is deleted with the rest.
     */
    suspend fun updateTransfer(groupId: TransferGroupId, draft: TransferDraft) {
        val existing = persistence.inGroup(groupId)
        if (existing.isEmpty()) throw DomainError.NotFound("Transfer")
        val plan = plan(draft)
        persistence.replaceGroup(groupId, members(groupId, plan, existing))
    }

    /** FR-131. Deletes by group id, which matches the fee row too, so a fee is never orphaned. */
    suspend fun deleteTransfer(groupId: TransferGroupId) = persistence.deleteAll(groupId)

    suspend fun members(groupId: TransferGroupId): List<Transaction> = persistence.inGroup(groupId)

    // ── Internals ──

    /** A draft that passed validation, with its accounts known to be present. */
    private class Plan(val draft: TransferDraft, val source: Account, val destination: Account, val fees: Category?)

    private suspend fun plan(draft: TransferDraft): Plan {
        val issues = TransferValidator.validate(draft)
        if (issues.isNotEmpty()) throw DomainError.TransferInvalid(issues)
        // Looked up before anything is built, so a missing Fees category fails with nothing written.
        val fees = if (draft.feeMinor != null) categories.feesCategory() else null
        // The validator reported SOURCE_REQUIRED / DESTINATION_REQUIRED otherwise.
        return Plan(draft, checkNotNull(draft.source), checkNotNull(draft.destination), fees)
    }

    private fun members(groupId: TransferGroupId, plan: Plan, existing: List<Transaction>): List<Transaction> {
        fun idOf(type: TransactionType) = existing.firstOrNull { it.type == type }?.id ?: TransactionId.random()
        val draft = plan.draft
        val out = Transaction(
            id = idOf(TransactionType.TRANSFER_OUT),
            amountMinor = draft.amountMinor,
            type = TransactionType.TRANSFER_OUT,
            date = draft.date,
            note = draft.note,
            accountId = plan.source.id,
            categoryId = null,
            transferGroupId = groupId,
            counterAccountId = plan.destination.id,
        )
        val into = out.copy(
            id = idOf(TransactionType.TRANSFER_IN),
            type = TransactionType.TRANSFER_IN,
            accountId = plan.destination.id,
            counterAccountId = plan.source.id,
        )
        val fee = if (plan.fees != null && draft.feeMinor != null) {
            Transaction(
                id = idOf(TransactionType.EXPENSE),
                amountMinor = draft.feeMinor,
                type = TransactionType.EXPENSE,
                date = draft.date,
                note = null,
                accountId = plan.source.id,
                categoryId = plan.fees.id,
                transferGroupId = groupId,
                counterAccountId = null, // a fee has one account
            )
        } else {
            null
        }
        return listOfNotNull(out, into, fee)
    }
}
