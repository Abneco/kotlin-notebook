// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketBase

fun JupyterSocketBase.setClientReply(message: RawMessage) {
  (this as? EmbeddedJupyterSocket)?.setClientReply(message)
}
