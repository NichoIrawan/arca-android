package org.nicho.arca.data.mapper

import java.util.UUID
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.model.CategoryId
import org.nicho.arca.domain.model.TransactionId
import org.nicho.arca.domain.model.TransferGroupId

// Ids are UUIDs in the domain and lowercase hyphenated strings in storage (SDD-ARC-002 §6.6).
// UUID.toString() is already lowercase, so the stored form is canonical.

internal fun AccountId.key(): String = value.toString()
internal fun CategoryId.key(): String = value.toString()
internal fun TransactionId.key(): String = value.toString()
internal fun TransferGroupId.key(): String = value.toString()

internal fun String.toAccountId() = AccountId(UUID.fromString(this))
internal fun String.toCategoryId() = CategoryId(UUID.fromString(this))
internal fun String.toTransactionId() = TransactionId(UUID.fromString(this))
internal fun String.toTransferGroupId() = TransferGroupId(UUID.fromString(this))
