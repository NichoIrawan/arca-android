package org.nicho.arca.domain.validation

// What a capture rule reports. :app maps each to a string resource; the copy names the problem
// and the fix and never blames (SDD-ARC-001 §11.3), e.g. AMOUNT_NOT_POSITIVE →
// "Masukkan jumlah lebih dari 0".
enum class ValidationIssue {
    AMOUNT_NOT_POSITIVE,
    FUTURE_DATE,
    CATEGORY_REQUIRED,
    CATEGORY_KIND_MISMATCH,
    NOTE_TOO_LONG,
    ACCOUNT_REQUIRED,
    ACCOUNT_ARCHIVED,
}
