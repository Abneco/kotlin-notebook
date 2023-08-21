// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Version
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlinx.jupyter.config.notebookKernelSpec
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingUtilityObject.resetSessionMetaInformation
import org.jetbrains.kotlinx.jupyter.plugin.util.createConcurrentDoubleKeyMap
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterKernelCommunicationClient
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterKernelDoesNotExistsException
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterClient
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterKernelId
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterSessionData
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSessionId
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
import java.util.concurrent.atomic.AtomicBoolean

typealias KernelId = JupyterKernelId
typealias KernelName = String
typealias SessionId = JupyterNotebookSessionId

class KotlinInProcessJupyterClient(
    private val rootDir: File
): JupyterClient, Disposable {
    private val idGenerator = IdGenerator()

    private val kernels = ConcurrentCollectionFactory.createConcurrentMap<KernelId, KotlinKernelProcessHandler>()

    private val sessions = createConcurrentDoubleKeyMap(
        JupyterSessionData::sessionId,
        JupyterSessionData::kernelId,
    )

    private val clientSessions = ConcurrentCollectionFactory.createConcurrentMap<KernelId, KernelZMQClientSession>()

    private val afterRestart = AtomicBoolean(false)

    override val fileContentsApi: CachingFileContentsApi by lazy {
        TreeCachingFileContentsApi(JavaIoFileContentsApi(rootDir))
    }

    override fun startKernel(project: Project, kernelName: String, notebookPath: Path): KernelId? {
        if (kernelName !in kernelSpecs) return null
        val id = KernelId(idGenerator.generate())
        val kernel = createKernelProcess(
            project,
            notebookPath,
            onBeforeStartNotify = { showKotlinNotebookServerManagementToolWindow(project, it) },
            onKernelTerminated = { _, _ ->
                val file = VirtualFileManager.getInstance().findFileByNioPath(notebookPath) ?: return@createKernelProcess
                val notebookFile = BackedNotebookVirtualFile.find(file) ?: return@createKernelProcess
                val isAfterRestart = afterRestart.get()
                if (!project.isDisposed && isAfterRestart) {
                    val document = runReadAction {
                        FileDocumentManager.getInstance().getDocument(notebookFile.file)
                    }
                    document?.let {
                        resetSessionMetaInformation(it, notebookFile.file, project)
                    }
                    afterRestart.compareAndSet(true, false)
                }
                if (!isAfterRestart && clientSessions.containsKey(id)) {
                    JupyterRuntimeService.getInstance(project).clearRuntime(file)
                }
                clientSessions.remove(id)
            }
        )
        Disposer.register(this, kernel)
        kernels[id] = kernel
        return id
    }

    override fun getKernelSpecs(): List<JupyterKernelSpec> {
        return kernelSpecs.values.toList()
    }

    override fun getKernelSpec(kernelName: KernelName): JupyterKernelSpec {
        return kernelSpecs[kernelName] ?: throw JupyterKernelDoesNotExistsException(null, kernelName)
    }

    override fun getDefaultKernelSpec(): KernelName {
        return DEFAULT_KERNEL_NAME
    }

    override suspend fun listSessionsAsync(context: HttpSession.Request.Context): List<JupyterSessionData> {
        return sessions.values().toList()
    }

    override fun createSession(project: Project, kernelName: KernelName, notebookPath: String): JupyterSessionData {
        val notebookFile = File(notebookPath).absoluteFile
        val kernelId = startKernel(project, kernelName, notebookFile.toPath()) ?: throw RuntimeException("Unknown kernel: $kernelName")

        val sessionId = SessionId(idGenerator.generate())
        val data = JupyterSessionData(
            sessionId,
            kernelId,
            notebookFile.invariantSeparatorsPath
        )
        sessions.put(data)
        return data
    }

    override fun deleteSession(sessionId: SessionId) {
        val sessionData = sessions.getByFirstKey(sessionId) ?: return
        killKernel(sessionData.kernelId)
        sessions.removeByFirstKey(sessionId)
    }

    private fun killKernel(kernelId: KernelId) {
        // Maybe we should send shutdown request here
        val kernelProcess = kernels.remove(kernelId) ?: return
        Disposer.dispose(kernelProcess)
    }

    override fun interrupt(kernelId: KernelId) {
        val clientSession = clientSessions[kernelId] ?: return
        val interruptMessage = JupyterInterruptRequestMessageBuilder(clientSession.sessionId).build()
        clientSession.send(interruptMessage)
    }

    override fun restart(kernelId: KernelId) {
        val sessionData = sessions.getBySecondKey(kernelId) ?: return
        killKernel(kernelId)
        sessions.removeByValue(sessionData)
        afterRestart.compareAndSet(false, true)
    }

    override fun createWebSocketClientForKernel(
        kernelId: KernelId,
        sessionId: SessionId,
        onMessage: (JupyterMessage) -> Unit
    ): JupyterKernelCommunicationClient {
        val processHandler = kernels[kernelId] ?: throw RuntimeException("No kernel with id $kernelId")
        val config = processHandler.kernelConfig
        val session = KernelZMQClientSession(sessionId, config, onMessage)
        clientSessions[kernelId] = session
        Disposer.register(this, session)
        return session
    }

    override fun dispose() {
        kernels.clear()
        sessions.clear()
        clientSessions.clear()
    }

    override suspend fun getServerVersions(): Iterable<Pair<JupyterClient.VersionKind, Version>> = emptyList()

    companion object {
        private const val DEFAULT_KERNEL_NAME = "kotlin"

        private val kernelSpecs: Map<KernelName, JupyterKernelSpec> = mapOf(
            DEFAULT_KERNEL_NAME to JupyterKernelSpecBase(
                notebookKernelSpec.displayName,
                notebookKernelSpec.language,
                notebookKernelSpec.name
            )
        )
    }
}