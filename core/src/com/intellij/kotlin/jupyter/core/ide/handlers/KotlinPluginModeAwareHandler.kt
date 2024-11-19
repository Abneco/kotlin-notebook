// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.ide.handlers

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

/**
 * Represents a handler that is aware of the mode in which the Kotlin plugin is operating,
 * e.g., K1 or K2 mode.
 *
 * Handlers implementing this interface should provide logic that is specific to the
 * plugin mode, both for K1 and K2.
 *
 * Designed approach is to use [createPluginModeAwareInstance] as a handle to create a required implementation
 * in a particular inheritor, or, to say, in an EP of this interface.
 *
 * @see ScriptingSupportUpdater
 */
interface KotlinPluginModeAwareHandler

val isK2ModeEnabled: Boolean
    get() = KotlinPluginModeProvider.isK2Mode()

inline fun <A, T> createPluginModeAwareInstance(
    arguments: A,
    k1InstanceFactory: (A) -> T,
    k2InstanceFactory: (A) -> T
): T {
    return when (KotlinPluginModeProvider.currentPluginMode) {
        KotlinPluginMode.K1 -> k1InstanceFactory(arguments)
        KotlinPluginMode.K2 -> k2InstanceFactory(arguments)
    }
}

inline fun <T> createPluginModeAwareInstance(
    k1InstanceFactory: () -> T,
    k2InstanceFactory: () -> T
): T {
    return when (KotlinPluginModeProvider.currentPluginMode) {
        KotlinPluginMode.K1 -> k1InstanceFactory()
        KotlinPluginMode.K2 -> k2InstanceFactory()
    }
}

internal inline fun <T : KotlinPluginModeAwareHandler> createPluginModeAwareInstance(
    k1InstanceFactory: () -> T,
    k2InstanceFactory: () -> T
): T {
    return createPluginModeAwareInstance(
        Unit,
        { k1InstanceFactory() },
        { k2InstanceFactory() }
    )
}

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