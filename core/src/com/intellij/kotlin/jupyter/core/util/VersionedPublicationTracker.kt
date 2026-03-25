// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks versioning for O(1) dirty checks.
 *
 * [stateVersion] increments on every data mutation.
 * [lastPublishedVersion] is set after successful update.
 * [pendingPublishVersion] holds the captured version in-between a commit process.
 */
internal class VersionedPublicationTracker {
    private val stateVersion = AtomicLong(0)
    private val lastPublishedVersion = AtomicLong(0)
    private val pendingPublishVersion = AtomicLong(-1)

    val needsPublishing: Boolean
        get() = stateVersion.get() != lastPublishedVersion.get()

    fun incrementVersion() {
        stateVersion.incrementAndGet()
    }

    /**
     * Captures current [stateVersion] for deferred publication.
     */
    fun preparePublication() {
        pendingPublishVersion.set(stateVersion.get())
    }

    /**
     * Sets [lastPublishedVersion] to the previously captured version.
     * No-op if [preparePublication] was not called.
     */
    fun commitPublication() {
        val captured = pendingPublishVersion.getAndSet(-1)
        if (captured >= 0) {
            lastPublishedVersion.set(captured)
        }
    }
}
