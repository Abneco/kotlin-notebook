// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Version
import org.jetbrains.kotlinx.jupyter.config.notebookKernelSpec
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingUtilityObject.resetSessionMetaInformation
import org.jetbrains.kotlinx.jupyter.plugin.util.DEFAULT_KOTLIN_KERNEL_NAME
import org.jetbrains.kotlinx.jupyter.plugin.util.createConcurrentDoubleKeyMap
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterKernelCommunicationClient
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterKernelDoesNotExistsException
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterClient
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterKernelId
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSessionId
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

typealias KernelName = String

interface KotlinKernelRunnableProvider {
    fun getKernel(kernelId: JupyterKernelId): KotlinKernelRunnableHandler?
}

class KotlinInProcessJupyterClient(
    private val rootDir: File
): JupyterClient, KotlinKernelRunnableProvider, Disposable {
    private val idGenerator = IdGenerator()

    private val kernels = ConcurrentCollectionFactory.createConcurrentMap<JupyterKernelId, KotlinKernelRunnableHandler>()

    private val sessions = createConcurrentDoubleKeyMap(
        JupyterSessionData::sessionId,
        JupyterSessionData::kernelId,
    )

    private val clientSessions = ConcurrentCollectionFactory.createConcurrentMap<JupyterKernelId, KotlinKernelSession>()

    override val fileContentsApi: CachingFileContentsApi by lazy {
        TreeCachingFileContentsApi(JavaIoFileContentsApi(rootDir))
    }

    override fun getKernel(kernelId: JupyterKernelId): KotlinKernelRunnableHandler? {
        return kernels[kernelId]
    }

    override fun startKernel(
        project: Project,
        kernelName: String,
        notebookPath: Path
    ): JupyterKernelId? {
        if (kernelName !in kernelSpecs) return null
        val kernelId = JupyterKernelId(idGenerator.generate())

        val kernel: KotlinKernelRunnableHandler = KernelRunnableFactory.createKernelRunnableHandler(
            project,
            kernelId,
            notebookPath,
        )
        kernel.addKernelListener(MyKernelListener())

        Disposer.register(this, kernel)
        kernels[kernelId] = kernel
        return kernelId
    }

    override fun getKernelSpecs(): List<JupyterKernelSpec> {
        return kernelSpecs.values.toList()
    }

    override fun getKernelSpec(kernelName: KernelName): JupyterKernelSpec {
        return kernelSpecs[kernelName] ?: throw JupyterKernelDoesNotExistsException(null, kernelName)
    }

    override fun getDefaultKernelSpec(): KernelName {
        return DEFAULT_KOTLIN_KERNEL_NAME
    }

    override suspend fun listSessionsAsync(context: HttpSession.Request.Context): List<JupyterSessionData> {
        return sessions.values().toList()
    }

    override fun createSession(project: Project, kernelName: KernelName, notebookPath: String): JupyterSessionData {
        val notebookFile = File(notebookPath).absoluteFile
        val kernelId = startKernel(project, kernelName, notebookFile.toPath()) ?: throw RuntimeException("Unknown kernel: $kernelName")

        val sessionId = JupyterNotebookSessionId(idGenerator.generate())
        val data = JupyterSessionData(
            sessionId,
            kernelId,
            notebookFile.invariantSeparatorsPath
        )
        sessions.put(data)
        return data
    }

    override fun deleteSession(sessionId: JupyterNotebookSessionId) {
        val sessionData = sessions.getByFirstKey(sessionId) ?: return
        killKernel(sessionData.kernelId)
        sessions.removeByFirstKey(sessionId)
    }

    private fun killKernel(kernelId: JupyterKernelId) {
        // Maybe we should send shutdown request here
        val kernelProcess = kernels.remove(kernelId) ?: return
        removeSessionAndRelatedState(kernelProcess)
        Disposer.dispose(kernelProcess)
    }

    override fun interrupt(kernelId: JupyterKernelId) {
        val clientSession = clientSessions[kernelId] ?: return
        val interruptMessage = JupyterInterruptRequestMessageBuilder(clientSession.sessionId).build()
        clientSession.send(interruptMessage)
    }

    override fun restart(kernelId: JupyterKernelId) {
        val sessionData = sessions.getBySecondKey(kernelId) ?: return
        killKernel(kernelId)
        sessions.removeByValue(sessionData)
    }

    override fun createWebSocketClientForKernel(
        kernelId: JupyterKernelId,
        sessionId: JupyterNotebookSessionId,
        onMessage: (JupyterMessage) -> Unit
    ): JupyterKernelCommunicationClient {
        val processHandler = kernels[kernelId] ?: throw RuntimeException("No kernel with id $kernelId")
        val session = processHandler.createSession(sessionId, onMessage)
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

    private fun removeSessionAndRelatedState(kernelHandler: KotlinKernelRunnableHandler) {
        if (removeSession(kernelHandler.kernelId) && kernelHandler.kernelState == KernelState.STARTED) {
            val notebookFile = kernelHandler.notebookVirtualFile ?: return
            val project = kernelHandler.project

            resetSessionMetaInformation(notebookFile.file, project)
            if (!project.isDisposed) {
                JupyterRuntimeService.getInstance(project).clearRuntime(notebookFile.file)
            }
        }
    }

    /**
     * Removes a session with the given kernelId from the clientSessions map and disposes the session.
     * If the session is successfully removed, returns true. Otherwise, returns false.
     *
     * @param kernelId The ID of the Jupyter kernel associated with the session.
     *
     * @return True if the session was successfully removed, false otherwise.
     */
    private fun removeSession(kernelId: JupyterKernelId): Boolean {
        return clientSessions.remove(kernelId)?.let { session ->
            Disposer.dispose(session)
            true
        } ?: false
    }

    private inner class MyKernelListener: KotlinKernelListener {
        override fun kernelTerminated(event: KotlinKernelEvent) {
            removeSessionAndRelatedState(event.source)
        }
    }

    companion object {
        private val kernelSpecs: Map<KernelName, JupyterKernelSpec> = mapOf(
            DEFAULT_KOTLIN_KERNEL_NAME to JupyterKernelSpecBase(
                notebookKernelSpec.displayName,
                notebookKernelSpec.language,
                notebookKernelSpec.name
            )
        )
    }
}
