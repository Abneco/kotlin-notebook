// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.attached

import com.intellij.jupyter.core.jupyter.connections.session.KernelStartupOptions
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.AbstractKotlinKernelConfigFactory
import com.intellij.openapi.project.Project
import com.intellij.util.lang.JavaVersion
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelPorts
import java.io.File
import java.nio.file.Path

class AttachedKernelConfigFactory(
    startupOptions: KernelStartupOptions,
    private val host: String,
    private val ports: KernelPorts,
    private val signature: String,
) : AbstractKotlinKernelConfigFactory(startupOptions) {
    override fun getHost(): String = host
    override fun getSignature(): String = signature
    override fun getKernelPorts(): KernelPorts = ports
    override fun getDebugPortOrNull(notebookPath: Path): Int? = null
    override fun getClasspath(): List<File> = emptyList()
    override fun getJvmTargetForSnippets(project: Project): JavaVersion? = null
}