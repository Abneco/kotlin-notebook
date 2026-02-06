// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.api

import com.intellij.kotlin.jupyter.test.KotlinNotebookTestCase
import com.intellij.kotlin.jupyter.test.runners.RunModeAwareTest
import com.intellij.testFramework.TestDataPath
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * This class contains tests for the API surface in [org.jetbrains.kotlinx.jupyter.api.Notebook] that
 * require tests on the IDEA side. The API should be fully tested by Kotlin Kernel unit tests, but some
 * functionality changes slightly when run from inside the IDE. These APIs must be tested here.
 */
@RunModeAwareTest
@TestDataPath($$"$CONTENT_ROOT/testData/notebooks/api")
class NotebookApiTests: KotlinNotebookTestCase() {

    @Test
    fun workingDir() = runNotebookTest {
        executeCell(0).let {
            val output = it.getTextPlainOutput()
            // Check for KTNB-1286
            // workingDir should always return an absolute path, but we do not control the location
            // nor the platform, so just fuzzy match on output. Example of output:
            // /private/var/folders/ll/s18kqj6566l5q88rrbs6jgm80000gn/T/unitTest_workingDir_36IH2KSvPrwtZStHSDb91x7VAbU/unitTest11300993571698510471
            output.isNotBlank() shouldBe true
            output.contains("unitTest_workingDir_")
        }
    }
}
