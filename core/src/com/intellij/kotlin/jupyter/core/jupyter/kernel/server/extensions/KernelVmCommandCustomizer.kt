// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.extensions

import com.intellij.openapi.extensions.ExtensionPointName

interface KernelVmCommandCustomizer {
    fun addVmArguments(arguments: MutableList<String>)

    companion object {
        private val EP = ExtensionPointName.create<KernelVmCommandCustomizer>("com.intellij.kotlin.jupyter.core.kernel.kernelVmCommandCustomizer")

        fun addVmArguments(arguments: MutableList<String>) {
            EP.extensions.forEach { it.addVmArguments(arguments) }
        }
    }
}