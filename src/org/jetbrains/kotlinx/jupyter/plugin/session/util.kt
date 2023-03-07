// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.session

import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession

fun JupyterNotebookSession.isKotlinNotebookSession(): Boolean {
    return kernelName.toLowerCaseAsciiOnly() == "kotlin"
}
