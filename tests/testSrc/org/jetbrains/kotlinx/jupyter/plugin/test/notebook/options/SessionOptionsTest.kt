// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.options

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import org.jetbrains.kotlinx.jupyter.plugin.settings.SessionOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.settings.generateSnippet
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SessionOptionsTest: BasePlatformTestCase() {
    @Test
    fun `initial snippet generation`() {
        val options = service<SessionOptionsProvider>()

        TestCase.assertEquals(
            """
                SessionOptions.resolveMpp = false
                SessionOptions.resolveSources = true
                SessionOptions.serializeScriptData = true

            """.trimIndent(),
            options.generateSnippet()
        )

        options.addListener(object: SessionOptionsProvider.Listener {
            override fun onResolveSourcesChanged(oldValue: Boolean, newValue: Boolean) {
                TestCase.assertEquals(true, oldValue)
                TestCase.assertEquals(false, newValue)
            }

            override fun onResolveMppChanged(oldValue: Boolean, newValue: Boolean) {
                TestCase.fail("resolveMpp shouldn't change")
            }
        }, testRootDisposable)

        options.resolveSources = false

        TestCase.assertEquals(
            """
                SessionOptions.resolveMpp = false
                SessionOptions.resolveSources = false
                SessionOptions.serializeScriptData = true

            """.trimIndent(),
            options.generateSnippet()
        )
    }
}
