// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import org.jetbrains.kotlinx.jupyter.api.AbstractKernelRunMode
import org.jetbrains.kotlinx.jupyter.api.StreamSubstitutionType
import org.jetbrains.kotlinx.jupyter.util.ClassLoadingDelegatingStrategy
import org.jetbrains.kotlinx.jupyter.util.DelegatingClassLoader
import org.jetbrains.kotlinx.jupyter.util.kernelFqnPrefixes

object IntellijProcessKernelRunMode : AbstractKernelRunMode("Intellij Process") {
    override fun createIntermediaryClassLoader(parent: ClassLoader) = createIdeDelegatingClassLoader(parent)

    override val shouldKillProcessOnShutdown: Boolean get() = false
    override val inMemoryOutputsSupported: Boolean get() = true
    override val isRunInsideIntellijProcess: Boolean get() = true
    override val streamSubstitutionType: StreamSubstitutionType
        get() = StreamSubstitutionType.NON_BLOCKING
}

private val librariesFqnPrefixes = listOf(
    "org.jetbrains.letsPlot."
)

private fun String.startsWithAnyOf(prefixes: List<String>): Boolean {
    val myString = this
    return prefixes.any { myString.startsWith(it) }
}

private fun createIdeDelegatingClassLoader(parent: ClassLoader): ClassLoader {
    val strategy =
        ClassLoadingDelegatingStrategy { classFqn: String, parentLoader: ClassLoader ->
            when {
                classFqn.startsWithAnyOf(kernelFqnPrefixes) -> parentLoader
                classFqn.startsWithAnyOf(librariesFqnPrefixes) -> null
                else -> parentLoader.parent
            }
        }

    return DelegatingClassLoader(parent, strategy)
}