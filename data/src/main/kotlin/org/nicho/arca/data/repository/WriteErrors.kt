package org.nicho.arca.data.repository

import android.database.sqlite.SQLiteException
import org.nicho.arca.domain.error.PersistenceError

// SDD-ARC-002 §8.3, §13. Every store write runs inside saveOrFail, so :app only ever sees
// DomainError or PersistenceError, never a SQLite type. A DomainError thrown inside the block
// isn't a SQLiteException, so it passes through untouched.
//
// Inline so the block can call suspend functions (DAO calls, withTransaction) directly.
internal inline fun <T> saveOrFail(block: () -> T): T =
    try {
        block()
    } catch (e: SQLiteException) {
        throw PersistenceError.SaveFailed(e)
    }

/** A write that matched no row is a failed save, never a silent success (NFR-R02). */
internal fun missing(what: String, id: String) =
    PersistenceError.SaveFailed(NoSuchElementException("No $what with id $id"))
