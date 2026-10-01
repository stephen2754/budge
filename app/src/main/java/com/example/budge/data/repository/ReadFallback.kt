package com.example.budge.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * Answers with [fallback] when reading the database fails.
 *
 * A Room flow reports a failure by throwing from the flow — an unreadable or corrupt file, a
 * query that cannot run — and these flows are collected in `viewModelScope` or straight from
 * composition, where an exception is not a message but a crash the reader cannot get past:
 * the app would die on every launch, at the same point, with no way back in. An empty answer
 * is something the screens already know how to draw, and it says what actually happened
 * (nothing was read) rather than pretending the ledger is empty — the writes that fail say so
 * where they happen.
 *
 * Cancellation is rethrown: a cancelled collection is not a read failure, and swallowing it
 * would leave the flow running after its collector is gone.
 */
fun <T> Flow<T>.fallingBackTo(fallback: T): Flow<T> =
    catch { cause ->
        if (cause is CancellationException) throw cause
        emit(fallback)
    }
