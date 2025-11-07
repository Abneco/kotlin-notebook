// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.nio.file.Path

/**
 * Responsible for customizing debug options for the kernel factory setup
 */
interface KernelDebugOptionsCustomizer {
    fun getDebugPort(project: Project, notebookPath: Path): Int?

    companion object {
        private val EP = ExtensionPointName.create<KernelDebugOptionsCustomizer>("com.intellij.kotlin.jupyter.core.kernel.debugOptionsCustomizer")

        fun getDebugPort(project: Project, notebookPath: Path): Int? {
            return EP.extensionList.firstNotNullOfOrNull {
                it.getDebugPort(project, notebookPath)
            }
        }
    }
}