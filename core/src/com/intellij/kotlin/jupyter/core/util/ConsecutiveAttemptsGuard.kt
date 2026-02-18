// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger

/**
 * This class is designed to aid writing repeat-until-failure logic.
 *
 * Tracks consecutive failures and triggers a [onThresholdReached] when the [attemptsThreshold] is reached.
 * Each successful attempt resets the [consecutiveFailures] counter.
 */
class ConsecutiveAttemptsGuard(
    private val attemptsThreshold: Int,
    private val onThresholdReached: () -> Unit = {},
    private val onAttemptFailure: (Int, Throwable) -> Unit = { _, _ -> },
) {
    private val consecutiveFailures: AtomicInteger = AtomicInteger(0)

    fun resetAttempts() {
        consecutiveFailures.set(0)
    }

    suspend fun <T> withConsecutiveAttempts(block: suspend () -> T): T {
        try {
            return block().also { consecutiveFailures.set(0) }
        }
        catch (e: CancellationException) {
            throw e
        }
        catch (e: Throwable) {
            val failureCount = consecutiveFailures.incrementAndGet()
            onAttemptFailure(failureCount, e)
            if (failureCount == attemptsThreshold) {
                onThresholdReached()
            }
            throw e
        }
    }
}
