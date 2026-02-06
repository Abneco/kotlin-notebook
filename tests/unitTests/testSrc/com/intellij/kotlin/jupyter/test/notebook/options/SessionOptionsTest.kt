// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.options

import com.intellij.kotlin.jupyter.core.settings.SessionOptionsProvider
import com.intellij.kotlin.jupyter.core.settings.generateSnippet
import com.intellij.kotlin.jupyter.test.KotlinNotebookUnitTestCase
import com.intellij.openapi.components.service
import org.junit.Test

class SessionOptionsTest: KotlinNotebookUnitTestCase() {
    @Test
    fun `initial snippet generation`() {
        val options = service<SessionOptionsProvider>()

        assertEquals(
            """
                SessionOptions.resolveSources = true
                SessionOptions.serializeScriptData = true

            """.trimIndent(),
            options.generateSnippet()
        )

        options.addListener(object: SessionOptionsProvider.Listener {
            override fun onResolveSourcesChanged(oldValue: Boolean, newValue: Boolean) {
                assertEquals(true, oldValue)
                assertEquals(false, newValue)
            }
        }, testRootDisposable)

        options.resolveSources = false

        assertEquals(
            """
                SessionOptions.resolveSources = false
                SessionOptions.serializeScriptData = true

            """.trimIndent(),
            options.generateSnippet()
        )
    }
}
