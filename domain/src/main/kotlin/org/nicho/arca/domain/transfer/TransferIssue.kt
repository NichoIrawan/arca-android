package org.nicho.arca.domain.transfer

// SDD-ARC-001 §6.3, plus the "required" and note cases the form needs.
enum class TransferIssue {
    SOURCE_REQUIRED,
    DESTINATION_REQUIRED,
    SAME_ACCOUNT,          // FR-132: "Pilih akun tujuan yang berbeda"
    AMOUNT_NOT_POSITIVE,   // DD-11
    FEE_NOT_POSITIVE,      // a fee, when given, is > 0
    FUTURE_DATE,           // FR-103
    ARCHIVED_ACCOUNT,      // FR-202
    NOTE_TOO_LONG,
}
