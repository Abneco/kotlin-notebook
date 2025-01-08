// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.execution

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.jupyter.core.jackson
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage

// This file contains helper functions for working with, and creating the expected JSON output
// from the Jupyter Kernel.

val JupyterMessage.messageData get() = messageContent["data"] as ObjectNode

/**
 * Build a generic JSON object.
 */
fun buildJacksonObject(buildAction: ObjectNode.() -> Unit): ObjectNode {
    return jackson.createObjectNode().apply(buildAction)
}

/**
 * Create the Kernel JSON structure representing plain text output.
 */
fun textPlainOutput(content: String): ObjectNode = buildJacksonObject {
    put("text/plain", content)
}

/**
 * Helper object for empty output. Empty output is actually represented as the empty string "",
 * but to make assertion code easier, we map it to an empty JSON object instead.
 */
fun emptyOutput(): ObjectNode = buildJacksonObject {
    // Nothing
}
