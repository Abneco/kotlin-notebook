// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.jupyter.core.jupyter.nbformat.MimeType
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

    /**
     * Return the plain text output of the cell. If the output did not contain a text/plain part,
     * an [IllegalStateException] is thrown.
     */
    fun getTextPlainOutput(): String {
        val mimeType = MimeType.TEXT_PLAIN.mimeType
        return output.get(mimeType)?.asText() ?: error("No $mimeType output found in cell execution result: $output")
    }
}

