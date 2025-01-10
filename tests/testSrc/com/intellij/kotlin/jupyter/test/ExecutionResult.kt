// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.Assert.assertEquals

/**
 * Type-safe wrapper for execution results when calling [NotebookTestBuilder.executeCell].
 */
class ExecutionResult(val output: ObjectNode) {

    /**
     * Check that the output matches the given output. If not, a test failure
     * is thrown.
     */
    fun assertOutput(expectedOutput: ObjectNode) {
        assertEquals(expectedOutput.toPrettyString(), output.toPrettyString())
    }
}

