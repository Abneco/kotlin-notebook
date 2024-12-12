// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.jupyter.core.jupyter.connections.client.JupyterClient
import com.intellij.jupyter.core.jupyter.connections.exceptions.JupyterKernelDoesNotExistsException
import com.intellij.jupyter.core.jupyter.connections.execution.JupyterKernelCommunicationClient
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterKernelId
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterSessionData
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterInterruptRequestMessageBuilder
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterShutdownRequestMessageBuilder
import com.intellij.jupyter.core.jupyter.connections.execution.notebook.JupyterRuntimeService
import com.intellij.jupyter.core.jupyter.connections.filecontentsapi.CachingFileContentsApi
import com.intellij.jupyter.core.jupyter.connections.http.HttpSession
import com.intellij.jupyter.core.jupyter.nbformat.JupyterKernel
import com.intellij.jupyter.core.jupyter.nbformat.JupyterKernelBase
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.util.resetSessionMetaInformation
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.events.JupyterSessionVerifiedListener
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.events.NotebookSessionEventListener
import com.intellij.kotlin.jupyter.core.notifications.notebookNotifications
import com.intellij.kotlin.jupyter.core.util.DEFAULT_KOTLIN_KERNEL_NAME
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.createConcurrentDoubleKeyMap
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlinx.jupyter.config.notebookKernelSpec
import java.io.File
import java.nio.file.Path

typealias KernelName = String

interface KotlinKernelRunnableProvider {
    fun getKernel(kernelId: JupyterKernelId): KotlinKernelRunnableHandler?
}

/**
 * Jupyter client that is running in the IDE process.
 */
class KotlinInProcessJupyterClient(
) : JupyterClient, KotlinKernelRunnableProvider, Disposable {
    init {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(JupyterSessionVerifiedListener.TOPIC, JupyterSessionVerifiedListener { project, virtualFile ->
                project.messageBus.syncPublisher(NotebookSessionEventListener.TOPIC)
                    .sessionStarted(virtualFile, isAfterRestart = pendingRestarts.remove(virtualFile.file))
            })
    }

    private val idGenerator = IdGenerator()

    private val kernelsHandlers = ConcurrentCollectionFactory.createConcurrentMap<JupyterKernelId, KotlinKernelRunnableHandler>()
    private val pendingRestarts = ConcurrentCollectionFactory.createConcurrentSet<VirtualFile>()

    private val sessions = createConcurrentDoubleKeyMap(
        JupyterSessionData::sessionId,
        JupyterSessionData::kernelId,
    )

    private val clientSessions = ConcurrentCollectionFactory.createConcurrentMap<JupyterKernelId, KotlinKernelSession>()

    override val fileContentsApi: CachingFileContentsApi
        get() = error("Kotlin is not support file contents")


    override val defaultKernel: JupyterKernel
        get() = kernelSpecs.values.first()
    override val kernels: List<JupyterKernel>
        get() = kernelSpecs.values.toList()

    override suspend fun uploadFile(filePath: String, content: ByteArray): String {
        TODO("Not yet implemented")
    }

    override suspend fun isFileExists(filePath: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun getKernel(kernelId: JupyterKernelId): KotlinKernelRunnableHandler? {
        return kernelsHandlers[kernelId]
    }

    fun startKernel(
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
        kernel.addBaseKernelListener(MyKernelListener())

        Disposer.register(this, kernel)
        kernelsHandlers[kernelId] = kernel
        return kernelId
    }


    override fun getKernelSpec(kernelName: KernelName): JupyterKernel {
        return kernelSpecs[kernelName] ?: throw JupyterKernelDoesNotExistsException(null, kernelName)
    }

    override suspend fun listSessionsAsync(context: HttpSession.Request.Context): List<JupyterSessionData> {
        return sessions.values().toList()
    }

    override suspend fun createSession(project: Project, kernelName: KernelName, notebookPath: String): JupyterSessionData {
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

    override suspend fun deleteSession(sessionId: JupyterNotebookSessionId) {
        val sessionData = sessions.getByFirstKey(sessionId) ?: return
        killKernel(sessionData.kernelId)
        sessions.removeByFirstKey(sessionId)
    }

    private fun killKernel(kernelId: JupyterKernelId) {
        sendShutdown(kernelId)
        val kernelProcess = kernelsHandlers.remove(kernelId) ?: return
        removeSessionAndRelatedState(kernelProcess)
        KotlinNotebookPluginScope.invokeOnEDT {
            Disposer.dispose(kernelProcess)
        }
    }

    private fun sendShutdown(kernelId: JupyterKernelId) {
        val clientSession = clientSessions[kernelId] ?: return
        val shutdownMessage = JupyterShutdownRequestMessageBuilder(clientSession.sessionId).build()

        try {
            clientSession.send(shutdownMessage)
        } catch (_: InterruptedException) {
            // It's fine to have an InterruptedException here in the embedded mode
        } catch (e: Throwable) {
            thisLogger().error(e)
        }
    }

    override suspend fun interrupt(kernelId: JupyterKernelId) {
        val clientSession = clientSessions[kernelId] ?: return
        val interruptMessage = JupyterInterruptRequestMessageBuilder(clientSession.sessionId).build()
        clientSession.send(interruptMessage)
    }

    override suspend fun restart(kernelId: JupyterKernelId) {
        val sessionData = sessions.getBySecondKey(kernelId) ?: return
        val project = kernelsHandlers[kernelId]?.project
        val notebookFile = kernelsHandlers[kernelId]?.notebookVirtualFile
        killKernel(kernelId)
        sessions.removeByValue(sessionData)

        if (notebookFile != null) {
            pendingRestarts.add(notebookFile.file)
        }

        project?.notebookNotifications?.showKernelRestart()
    }

    override fun createWebSocketClientForKernel(
        kernelId: JupyterKernelId,
        sessionId: JupyterNotebookSessionId,
        onMessage: (JupyterMessage) -> Unit
    ): JupyterKernelCommunicationClient? {
        val processHandler = kernelsHandlers[kernelId] ?: throw RuntimeException("No kernel with id $kernelId")
        val session = processHandler.createSession(sessionId, onMessage) ?: return null
        clientSessions[kernelId] = session
        Disposer.register(this, session)
        return session
    }

    override fun dispose() {
        kernelsHandlers.clear()
        sessions.clear()
        clientSessions.clear()
    }


    private fun removeSessionAndRelatedState(kernelHandler: KotlinKernelRunnableHandler) {
        if (removeSession(kernelHandler.kernelId) && kernelHandler.kernelState != KernelState.STARTING) {
            val notebookFile = kernelHandler.notebookVirtualFile ?: return
            val project = kernelHandler.project

            // probably move out from here
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
        } == true
    }

    private inner class MyKernelListener : KotlinKernelListener {
        override fun kernelTerminated(event: KotlinKernelEvent) {
            removeSessionAndRelatedState(event.source)
        }
    }

    companion object {
        private val kernelSpecs: Map<KernelName, JupyterKernel> = mapOf(
            DEFAULT_KOTLIN_KERNEL_NAME to JupyterKernelBase(
                notebookKernelSpec.displayName,
                notebookKernelSpec.language,
                notebookKernelSpec.name
            )
        )
    }
}
