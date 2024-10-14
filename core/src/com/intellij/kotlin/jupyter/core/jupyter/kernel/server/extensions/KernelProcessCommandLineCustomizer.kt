// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.extensions

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.extensions.ExtensionPointName

interface KernelProcessCommandLineCustomizer {
    fun customize(commandLine: GeneralCommandLine)

    companion object {
        private val EP = ExtensionPointName.create<KernelProcessCommandLineCustomizer>("com.intellij.kotlin.jupyter.core.kernel.kernelProcessCommandLineCustomizer")

        fun customize(commandLine: GeneralCommandLine) {
            EP.extensions.forEach { it.customize(commandLine) }
        }
    }
}