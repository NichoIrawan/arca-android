package org.nicho.arca.domain.error

// SDD-ARC-002 §13. Storage failures, in domain terms, so :app never sees a SQLite type.
sealed class PersistenceError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** Recoverable: show a retryable Snackbar. A failed write never reports success (NFR-R02). */
    class SaveFailed(cause: Throwable) : PersistenceError("Save failed", cause)

    class FetchFailed(cause: Throwable) : PersistenceError("Fetch failed", cause)

    /** Fatal: a blocking screen. Never fall back to a fresh empty database. */
    class DatabaseUnavailable(cause: Throwable? = null) : PersistenceError("Database unavailable", cause)
}
