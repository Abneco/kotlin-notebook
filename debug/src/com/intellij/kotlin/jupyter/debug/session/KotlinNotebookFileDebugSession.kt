// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.session

import com.intellij.debugger.DebugEnvironment
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.executor.JupyterExecutionListener
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.debugger.common.JupyterDebugSessionPath
import com.intellij.jupyter.core.jupyter.variables.common.JupyterEnvironmentUpdateListener
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.notifications.notebookNotifications
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.NotebookScriptsStateListener
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.NotebookScriptsStateListener.Companion.isIncomplete
import com.intellij.kotlin.jupyter.core.util.NotebookPerFileChildService
import com.intellij.kotlin.jupyter.core.util.runSafely
import com.intellij.kotlin.jupyter.debug.breakpoint.KernelBreakpointController
import com.intellij.kotlin.jupyter.debug.events.NotebookDebugEventsHandler
import com.intellij.kotlin.jupyter.debug.i18n.KotlinNotebookDebugBundle
import com.intellij.kotlin.jupyter.debug.listeners.KotlinNotebookDebugSessionListener
import com.intellij.kotlin.jupyter.debug.listeners.NOTEBOOK_DEBUG_SESSION_TOPIC
import com.intellij.kotlin.jupyter.debug.session.lifecycle.NotebookDebuggerSessionState
import com.intellij.kotlin.jupyter.debug.session.ui.NotebookDebugTabHandler
import com.intellij.kotlin.jupyter.debug.util.DebugSessionConfig
import com.intellij.kotlin.jupyter.debug.util.connection.DebugConnectionUtility
import com.intellij.kotlin.jupyter.debug.util.connection.DebugConnectionUtility.attachDebuggerCreateSession
import com.intellij.kotlin.jupyter.debug.util.connection.NotebookDebugProcessListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xdebugger.XDebugSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import kotlin.contracts.ExperimentalContracts
import kotlin.time.Duration.Companion.seconds

class KotlinNotebookFileDebugSession(
    public override val virtualFile: BackedNotebookVirtualFile,
    private val project: Project,
    coroutineScope: CoroutineScope,
    private val portProvider: () -> Int?
): NotebookPerFileChildService(virtualFile, coroutineScope) {
    private val currentConfigRef: AtomicReference<DebugSessionConfig?> = AtomicReference(null)
    private val debuggerSessionRef = AtomicReference<DebuggerSession?>(null)
    private val sessionMutex = Mutex()

    /**
     * Current state of the debug session.
     * Used to coordinate session lifecycle (initialization, disposal).
     */
    private val sessionState = MutableStateFlow(NotebookDebuggerSessionState.Absent)
    private val eventsHandler = NotebookDebugEventsHandler(project, virtualFile)
    private val tabHandler = NotebookDebugTabHandler(project, virtualFile.file.name, LOG)

    private val breakpointController = KernelBreakpointController(
        project, virtualFile, eventsHandler
    ) {
        sessionState.value = NotebookDebuggerSessionState.Ready
        project.messageBus.syncPublisher(NOTEBOOK_DEBUG_SESSION_TOPIC).onSessionInitialized(virtualFile)
    }

    init {
        project.initServiceListeners(this)

        // Subscribe to process detached events from NotebookDebugProcessListener
        project.messageBus.connect(this).subscribe(
            NOTEBOOK_DEBUG_SESSION_TOPIC,
            object : KotlinNotebookDebugSessionListener {
                override fun onProcessDetached(notebookFile: BackedNotebookVirtualFile) {
                    if (notebookFile != virtualFile) return
                    sessionState.value = NotebookDebuggerSessionState.Absent
                }
            }
        )

        val port = portProvider()
        if (port != null) {
            currentConfigRef.set(DebugSessionConfig(port))
        }
    }

    private fun Project.initServiceListeners(parentDisposable: Disposable) {
        val messageBus = messageBus
        messageBus.connect(parentDisposable).subscribe(
            NotebookScriptsStateListener.TOPIC,
            object : NotebookScriptsStateListener {
                override fun scriptsConfigurationUpdated(
                    file: BackedNotebookVirtualFile,
                    updateState: NotebookScriptsStateListener.UpdateState
                ) {
                    if (project.isDisposed) return
                    if (file != virtualFile || updateState.isIncomplete || debuggerSession?.isConnecting == true) return

                    messageBus.syncPublisher(JupyterEnvironmentUpdateListener.TOPIC)
                        .onRuntimeEnvironmentUpdate(virtualFile, null)
                }
            }
        )

        JupyterExecutionListener.register(parentDisposable, object : JupyterExecutionListener {
            override suspend fun sessionCreated(session: JupyterNotebookSession) {
                if (project.isDisposed) return
                if (session.virtualFile != virtualFile) return

                val port = targetDebugPort ?: provideFreshDebugPortOrNull() ?: return
                val debuggerSession = getOrCreateVmDebuggerSession(
                    DebugSessionConfig(port),
                    forceRestart = true
                )
                LOG.info("Debugger session after start: $debuggerSession")
            }
        })
    }

    val currentStackFrameProxy: StackFrameProxyImpl?
        get() = debuggerSession?.process?.debuggerContext?.frameProxy

    val evaluationContext: EvaluationContextImpl?
        get() = breakpointController.evaluationContext

    val targetDebugPort: Int? get() = currentConfigRef.get()?.port

    fun provideFreshDebugPortOrNull(): Int? {
        val port = portProvider() ?: return null
        currentConfigRef.set(DebugSessionConfig(port))
        return port
    }

    /**
     * Executes the given [action] with a synthetic breakpoint disabled,
     * restoring the original state afterward.
     *
     * If the debug context is not available, [action] is executed without breakpoint manipulation.
     */
    @RequiresBackgroundThread
    @OptIn(ExperimentalContracts::class)
    internal suspend inline fun withNonSuspendingBreakpoint(action: suspend () -> Unit) =
        breakpointController.withNonSuspendingBreakpoint(debuggerSession, action)

    val currentXSession: XDebugSession?
        get() = debuggerSessionRef.get()?.xDebugSession

    val debuggerSession: DebuggerSession?
        get() = debuggerSessionRef.get()

    val isLiveSession: Boolean
        get() = currentXSession != null

    suspend fun awaitInitialized() {
        sessionState.first { it == NotebookDebuggerSessionState.Ready }
    }

    fun showSessionTab() {
        tabHandler.showSessionTab(currentXSession)
    }

    // see JavaAttachDebuggerProvider
    internal suspend fun getOrCreateVmDebuggerSession(
        config: DebugSessionConfig,
        forceRestart: Boolean = false
    ): DebuggerSession? {
        sessionMutex.withLock {
            if (project.isDisposed) return null

            return when (val result = tryReuseExistingSession(config, forceRestart)) {
                is SessionConfigurationResult.ShouldReuse -> {
                    result.session
                }
                SessionConfigurationResult.NeedsNewSession -> {
                    disposeCurrentSession().await()
                    createNewDebuggerSession(config)?.also { newSession ->
                        currentConfigRef.set(config)
                        debuggerSessionRef.set(newSession)
                    }
                }
            }
        }
    }

    /**
     * Creates a new silent session using already
     * existing config to attach to the target VM.
     */
    internal suspend fun recreateSilentSession() {
        val currentConfig = currentConfigRef.get() ?: return
        if (currentConfig.silent) {
            LOG.info("Silent session already exists in ${virtualFile.file.name}, skipping recreation")
            return
        }

        sessionMutex.withLock {
            disposeSessionAndTab()
        }

        val newConfig = DebugSessionConfig(currentConfig.port, silent = true)
        val newSession = getOrCreateVmDebuggerSession(newConfig)
        if (newSession == null) {
            LOG.warn("Failed to recreate silent session for ${virtualFile.file.name}")
        }
    }

    private fun DebuggerSession.configureSessionAfterAttach(config: DebugSessionConfig) {
        addProcessListener(process, config.silent)

        if (!config.silent) {
            showDebugSupportNotification()
        }
    }

    private suspend fun tryReuseExistingSession(
        requestedConfig: DebugSessionConfig,
        forceRestart: Boolean
    ): SessionConfigurationResult {
        if (!checkSessionCanBeReused(requestedConfig, forceRestart)) {
            return SessionConfigurationResult.NeedsNewSession
        }

        val existingSession = debuggerSession ?: return SessionConfigurationResult.NeedsNewSession

        currentXSession?.applyVisibilityTransition(requestedConfig)
        currentConfigRef.set(requestedConfig)

        return SessionConfigurationResult.ShouldReuse(existingSession)
    }

    /**
     * Applies breakpoint muting based on the requested configuration.
     * Note: UI visibility transitions are handled by creating new sessions with forceRestart.
     * In split debugger mode, there's no way to show/hide debug tabs via RunContentManager.
     */
    private suspend fun XDebugSession.applyVisibilityTransition(newConfig: DebugSessionConfig) {
        val currentConfig = currentConfigRef.get()
        val wasSilent = currentConfig?.silent == true
        val wantsVisible = !newConfig.silent

        when {
            wasSilent && wantsVisible -> {
                LOG.info("Transitioning from silent to visible mode - use forceRestart for UI")
                readAction { setBreakpointMuted(false) }
                showDebugSupportNotification()
            }
            !wasSilent -> {
                readAction { setBreakpointMuted(!wantsVisible) }
            }
            // Both silent - no action needed
        }
    }

    private fun showDebugSupportNotification() {
        project.notebookNotifications.showDebugSupportInfo(
            KotlinNotebookDebugBundle.message("kotlin.jupyter.debug.support.text")
        )
    }

    private fun checkSessionCanBeReused(config: DebugSessionConfig, forceRestart: Boolean): Boolean {
        if (forceRestart) return false
        if (!isLiveSession || sessionState.value == NotebookDebuggerSessionState.Absent) return false

        val currentConfig = currentConfigRef.get() ?: return false
        if (currentConfig.port != config.port) return false

        // In Split mode, we can't hide the tab once it's shown.
        // Can reuse in all cases except visible → silent transition.
        return currentConfig.silent || !config.silent
    }

    /**
     * Note: Should be called before creating debug environments.
     */
    private fun configureGlobalDebuggerTransport(transport: Int) {
        DebuggerSettings.getInstance().transport = transport
    }

    private suspend fun createNewDebuggerSession(config: DebugSessionConfig): DebuggerSession? {
        return when (val result = createDebuggerSessionInternal(config)) {
            is SessionCreationResult.Success -> {
                sessionState.value = NotebookDebuggerSessionState.Initializing
                result.session
            }
            is SessionCreationResult.Failed -> {
                sessionState.value = NotebookDebuggerSessionState.Absent
                LOG.warn("Failed to create debugger session: ${result.reason}")
                null
            }
        }
    }

    private suspend fun createDebuggerSessionInternal(config: DebugSessionConfig): SessionCreationResult {
        configureGlobalDebuggerTransport(config.transport)

        val environmentData = DebugConnectionUtility.buildDebugEnvironment(project, config, virtualFile)
            ?: return SessionCreationResult.Failed("Failed to build debug environment")

        val newSession = withContext(Dispatchers.EDT) {
            try {
                tryAttachToTargetVM(
                    environmentData.debugEnvironment,
                    environmentData.executionEnvironment,
                    config.silent
                )
            } catch (e: Exception) {
                LOG.warn("Failed to attach to target VM", e)
                null
            }
        } ?: return SessionCreationResult.Failed("Failed to attach to target VM")

        return runSafely(
            {
                newSession.configureSessionAfterAttach(config)
                SessionCreationResult.Success(newSession)
            }, onFailure = {
                LOG.warn("Failed to configure debugger session", it)
                newSession.dispose()
                SessionCreationResult.Failed("Session configuration failed: ${it.message}")
            }
        ) ?: SessionCreationResult.Failed("Session configuration returned null")
    }

    private fun clearDebugSessionData() {
        debuggerSessionRef.set(null)
        currentConfigRef.set(null)
        breakpointController.clearContext()
        // NB: sessionState is set to Idle by onProcessDetached callback
    }

    @RequiresEdt
    private fun tryAttachToTargetVM(
        debugEnvironment: DebugEnvironment,
        executionEnvironment: ExecutionEnvironment,
        isSilent: Boolean
    ): DebuggerSession? {
        if (project.isDisposed) {
            return null
        }

        val session = executionEnvironment
            .attachDebuggerCreateSession(virtualFile.file.name, project, debugEnvironment, isHeadlessMode = isSilent)

        val handler = session.process.processHandler
        if (isSilent) {
            // important
            handler?.startNotify()
        }

        val registeredProcess = DebuggerManagerEx.getInstanceEx(project).getDebugProcess(handler)
        if (registeredProcess == null) {
            LOG.warn("DebugProcess is not registered for the handler right after session start")
        }
        return session
    }

    private fun addProcessListener(debugProcess: DebugProcessImpl?, silent: Boolean) {
        if (debugProcess == null) return
        val listener = NotebookDebugProcessListener(
            project,
            JupyterDebugSessionPath(virtualFile),
            virtualFile,
            breakpointController,
            silent
        )

        debugProcess.addDebugProcessListener(
            listener, this
        )
    }

    private suspend fun disposeSessionAndTab() {
        val contentDescriptor = withContext(Dispatchers.EDT) {
            tabHandler.findSessionTabDescriptor(currentXSession)
        }

        disposeCurrentSession().await()
        tabHandler.closeSessionTab(contentDescriptor)
    }

    /**
     * Returns a [Deferred] that completes when the process has been fully detached.
     * Uses a timeout to prevent the deferred from hanging indefinitely if onProcessDetached
     * is never called (e.g., process already detached, crash, or other unexpected conditions).
     */
    internal fun disposeCurrentSession(): Deferred<Unit> {
        val session = currentXSession ?: return CompletableDeferred(Unit)

        // Only proceed if we successfully claimed the transition
        var shouldProceed = false
        sessionState.update { current ->
            when (current) {
                NotebookDebuggerSessionState.Absent,
                NotebookDebuggerSessionState.Disposing -> current
                else -> {
                    shouldProceed = true
                    NotebookDebuggerSessionState.Disposing
                }
            }
        }

        if (!shouldProceed) {
            return CompletableDeferred(Unit)
        }

        // Completes via DebugProcessListener#onProcessDetached
        val deferred = coroutineScope.async {
            val awaitResult = withTimeoutOrNull(DISPOSE_TIMEOUT_SC) {
                sessionState.first { it == NotebookDebuggerSessionState.Absent }
            }
            if (awaitResult == null) {
                LOG.warn("Dispose timeout reached for ${virtualFile.file.name}, forcing state to Absent")
                sessionState.value = NotebookDebuggerSessionState.Absent
            }
        }

        runSafely({
            session.stop()
        }, onFailure = {
            LOG.error("Failed to dispose current session", it)
            sessionState.value = NotebookDebuggerSessionState.Absent
        }, finally = {
            clearDebugSessionData()
        })

        return deferred
    }

    override fun dispose() {
        // Disposal happens only with parent disposal
        disposeCurrentSession()
    }

    companion object {
        private val LOG = notebookLogger()

        /** Timeout for waiting on session disposal to prevent indefinite hangs. */
        private val DISPOSE_TIMEOUT_SC = 10.seconds

        /**
         * Result of attempting to reuse an existing debug session.
         */
        private sealed class SessionConfigurationResult {
            data class ShouldReuse(val session: DebuggerSession) : SessionConfigurationResult()
            data object NeedsNewSession : SessionConfigurationResult()
        }

        /**
         * Result of attempting to create a new debug session.
         */
        private sealed class SessionCreationResult {
            data class Success(val session: DebuggerSession) : SessionCreationResult()
            data class Failed(val reason: String) : SessionCreationResult()
        }
    }
}