// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.scripting

import com.intellij.kotlin.jupyter.core.ide.handlers.createPluginModeAwareInstance
import com.intellij.kotlin.jupyter.test.ensureScriptConfigurations
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

/**
 * This interface performs post scripting update actions required for a specific Kotlin mode,
 * like loading and updating configurations, etc.
 */
fun interface PostScriptingUpdateKotlinModeAwareHandler {
    fun handleAfterScriptingUpdate(testFixture: CodeInsightTestFixture)

    companion object {
        fun handleAfterScriptingUpdate(testFixture: CodeInsightTestFixture) = createPluginModeAwareInstance(
            testFixture,
            ::createK1Handler,
            ::createK2Handler,
        ).handleAfterScriptingUpdate(testFixture)

        // Required to provide script configurations into base cache
        private fun createK1Handler(testFixture: CodeInsightTestFixture) = PostScriptingUpdateKotlinModeAwareHandler {
            ensureScriptConfigurations(testFixture)
        }

        private fun createK2Handler(testFixture: CodeInsightTestFixture) = PostScriptingUpdateKotlinModeAwareHandler {}
    }
}