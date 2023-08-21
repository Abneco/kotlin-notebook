// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.extensions

import com.intellij.openapi.extensions.ExtensionPointName

interface KernelVmCommandCustomizer {
    fun addVmArguments(arguments: MutableList<String>)

    companion object {
        private val EP = ExtensionPointName.create<KernelVmCommandCustomizer>("org.jetbrains.kotlinx.jupyter.plugin.kernel.kernelVmCommandCustomizer")

        fun addVmArguments(arguments: MutableList<String>) {
            EP.extensions.forEach { it.addVmArguments(arguments) }
        }
    }
}