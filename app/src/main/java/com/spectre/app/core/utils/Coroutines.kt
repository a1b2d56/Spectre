package com.spectre.app.core.utils

import kotlinx.coroutines.CancellationException

/**
 * A highly durable wrapper around [runCatching] that respects Kotlin's structured concurrency.
 * If a Coroutine is cancelled, this will explicitly re-throw the [CancellationException] instead of swallowing it,
 * preventing silent failures and detached coroutines.
 */
inline fun <T> suspendRunCatching(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
