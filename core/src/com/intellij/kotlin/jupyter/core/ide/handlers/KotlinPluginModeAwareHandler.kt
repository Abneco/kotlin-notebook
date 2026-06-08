// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.ide.handlers

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

