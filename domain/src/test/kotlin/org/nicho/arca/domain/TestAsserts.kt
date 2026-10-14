package org.nicho.arca.domain

/**
 * JUnit's assertThrows takes a plain lambda, so it can't call a suspend function. Inline lambdas
 * can, when the caller is already suspending (inside runTest). Returns the exception so a test
 * can inspect it.
 */
internal inline fun <reified T : Throwable> assertThrowsSuspend(block: () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return e
        throw AssertionError("Expected ${T::class.simpleName} but got $e", e)
    }
    throw AssertionError("Expected ${T::class.simpleName} but nothing was thrown")
}
