// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.session

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.util.io.BaseOutputReader
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import java.nio.file.Path

class KotlinKernelProcessHandler(
    commandLine: GeneralCommandLine,
    val kernelConfig: KernelConfig,
    val notebookPath: Path,

    val onKernelTerminated: (ProcessEvent, KotlinKernelProcessHandler) -> Unit,
): KillableColoredProcessHandler(commandLine), Disposable {

    init {
        val handler = this

        addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                LOG.debug (event.text.trimEnd().trimStart('\r', '\n'))
            }

            override fun processTerminated(event: ProcessEvent) {
                onKernelTerminated(event, handler)
            }
        })
    }

    override fun dispose() {
        killProcess()
    }

    override fun readerOptions(): BaseOutputReader.Options {
        return BaseOutputReader.Options.forMostlySilentProcess()
    }

    companion object {
        private val LOG = Logger.getInstance(KotlinKernelProcessHandler::class.java)
    }
}