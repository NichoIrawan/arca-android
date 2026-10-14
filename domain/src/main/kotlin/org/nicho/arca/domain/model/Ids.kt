package org.nicho.arca.domain.model

import java.util.UUID

// SDD-ARC-002 §7.1. Value classes so an AccountId can't be passed where a CategoryId is
// expected; they compile down to the bare UUID, so they cost nothing at runtime.

@JvmInline
value class AccountId(val value: UUID) {
    companion object {
        fun random() = AccountId(UUID.randomUUID())
    }
}

@JvmInline
value class CategoryId(val value: UUID) {
    companion object {
        fun random() = CategoryId(UUID.randomUUID())
    }
}

@JvmInline
value class TransactionId(val value: UUID) {
    companion object {
        fun random() = TransactionId(UUID.randomUUID())
    }
}

@JvmInline
value class TransferGroupId(val value: UUID) {
    companion object {
        fun random() = TransferGroupId(UUID.randomUUID())
    }
}
