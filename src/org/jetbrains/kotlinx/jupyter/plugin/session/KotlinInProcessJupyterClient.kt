// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlinx.jupyter.config.notebookKernelSpec
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterKernelCommunicationClient
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterKernelDoesNotExistsException
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterClient
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterSessionData
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterInterruptRequestMessageBuilder
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.filecontentsapi.CachingFileContentsApi
import org.jetbrains.plugins.notebooks.jupyter.connections.filecontentsapi.JavaIoFileContentsApi
import org.jetbrains.plugins.notebooks.jupyter.connections.filecontentsapi.TreeCachingFileContentsApi
import org.jetbrains.plugins.notebooks.jupyter.connections.http.HttpSession
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterKernelSpec
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterKernelSpecBase
import java.io.File
import java.nio.file.Path

typealias KernelId = String
typealias KernelName = String
typealias SessionId = String

class KotlinInProcessJupyterClient(
    private val rootDir: File
): JupyterClient, Disposable {
    private val idGen = IdGenerator()

    private val processService = KotlinKernelProcessService.getInstance()

    private val kernels = mutableMapOf<KernelId, KotlinKernelProcessHandler>()
    private fun getKernel(id: KernelId): KotlinKernelProcessHandler {
        return kernels[id] ?: throw RuntimeException("No kernel with id $id")
    }

    private val sessions = mutableMapOf<SessionId, JupyterSessionData>()
    private val sessionsByKernelId = mutableMapOf<KernelId, JupyterSessionData>()
    private val clientSessions = mutableMapOf<KernelId, KernelZMQClientSession>()

    private val defaultKernel = "kotlin"

    private val kernelSpecs: Map<KernelName, JupyterKernelSpec> = mapOf(
        defaultKernel to JupyterKernelSpecBase(
            notebookKernelSpec.displayName,
            notebookKernelSpec.language,
            notebookKernelSpec.name
        )
    )

    override val fileContentsApi: CachingFileContentsApi
        get() = TreeCachingFileContentsApi(JavaIoFileContentsApi(rootDir))

    override fun startKernel(project: Project, kernelName: String, workingDir: Path): String? {
        if (kernelName !in kernelSpecs) return null
        val id = idGen.generate()
        val kernel = processService.create(project, workingDir)
        Disposer.register(this, kernel)
        kernels[id] = kernel
        return id
    }

    // private fun startKer

    override fun getKernelSpecs(): List<JupyterKernelSpec> {
        return kernelSpecs.values.toList()
    }

    override fun getKernelSpec(kernelName: KernelName): JupyterKernelSpec {
        return kernelSpecs[kernelName] ?: throw JupyterKernelDoesNotExistsException(null, kernelName)
    }

    override fun getDefaultKernelSpec(): KernelName {
        return defaultKernel
    }

    override suspend fun listSessionsAsync(context: HttpSession.Request.Context): List<JupyterSessionData> {
        return sessions.values.toList()
    }

    override fun createSession(project: Project, kernelName: KernelName, notebookPath: String): JupyterSessionData {
        val notebookFile = File(notebookPath).absoluteFile
        val workingDir = notebookFile.parentFile
        val kernelId = startKernel(project, kernelName, workingDir.toPath()) ?: throw RuntimeException("Unknown kernel: $kernelName")

        val sessionId = idGen.generate()
        val data = JupyterSessionData(
            sessionId,
            kernelId,
            notebookFile.invariantSeparatorsPath
        )
        sessions[sessionId] = data
        sessionsByKernelId[kernelId] = data
        return data
    }

    override fun deleteSession(sessionId: SessionId) {
        val data = sessions[sessionId] ?: return
        sessions.remove(sessionId)
        sessionsByKernelId.remove(data.kernelId)
    }

    private fun killKernel(kernelId: KernelId) {
        // Maybe we should send shutdown request here
        kernels[kernelId]?.killProcess()
    }

    override fun interrupt(kernelId: KernelId) {
        val clientSession = clientSessions[kernelId] ?: return
        val interruptMessage = JupyterInterruptRequestMessageBuilder(clientSession.sessionId).build()
        clientSession.send(interruptMessage)
    }

    override fun restart(kernelId: KernelId) {
        val sessionData = sessionsByKernelId[kernelId] ?: return
        killKernel(kernelId)
        deleteSession(sessionData.sessionId)
    }

    override fun createWebSocketClientForKernel(kernelId: KernelId, sessionId: SessionId, onMessage: (JupyterMessage) -> Unit): JupyterKernelCommunicationClient {
        val processHandler = getKernel(kernelId)
        val config = processHandler.kernelConfig
        val session = KernelZMQClientSession(sessionId, config, onMessage)
        clientSessions[kernelId] = session
        Disposer.register(this, session)
        return session
    }

    override fun dispose() {
        kernels.clear()
        sessions.clear()
        sessionsByKernelId.clear()
        clientSessions.clear()
    }
}