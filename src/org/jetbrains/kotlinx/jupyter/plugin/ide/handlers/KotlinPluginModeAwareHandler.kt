// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.ide.handlers

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

/**
 * Represents a handler that is aware of the mode in which the Kotlin plugin is operating.
 *
 * This sealed interface allows for the implementation of specific handlers for different
 * modes of the Kotlin plugin. The mode can typically be K1 or K2, representing different
 * versions or types of the Kotlin plugin.
 *
 * Handlers implementing this interface should encapsulate logic that is specific to the
 * plugin mode they are meant to support, ensuring that behavior can be appropriately
 * switched based on the current mode.
 *
 * This interface is typically used in conjunction with the `createPluginModeAwareInstance`
 * function to dynamically instantiate the correct handler based on the current `KotlinPluginMode`.
 */
sealed interface KotlinPluginModeAwareHandler

val isK2ModeEnabled: Boolean
    get() = KotlinPluginModeProvider.isK2Mode()

internal inline fun <A, T : KotlinPluginModeAwareHandler> createPluginModeAwareInstance(
    arguments: A,
    k1InstanceFactory: (A) -> T,
    k2InstanceFactory: (A) -> T
): T {
    return when (KotlinPluginModeProvider.currentPluginMode) {
        KotlinPluginMode.K1 -> k1InstanceFactory(arguments)
        KotlinPluginMode.K2 -> k2InstanceFactory(arguments)
    }
}