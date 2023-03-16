// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinCellExecutionCallbackFactory
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.scripting.JupyterKtScriptingSupport
import org.jetbrains.kotlinx.jupyter.plugin.session.isKotlinNotebookSession
import org.jetbrains.kotlinx.jupyter.plugin.util.KotlinNotebookCodegen
import org.jetbrains.plugins.notebooks.editor.NotebookEditorCreatedCallback
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.plugins.notebooks.jupyter.editor.isJupyter

class KotlinNotebookEditorFactoryListener : NotebookEditorCreatedCallback {
    override fun editorCreated(editor: Editor) {
        if (editor.isJupyter) {
            editor as EditorImpl
            val file = FileDocumentManager.getInstance().getFile(editor.document)
            if (file.isKotlinNotebook) {
                installSessionOptionsInitializer(editor)
            }
        }
    }

    private fun installSessionOptionsInitializer(editor: EditorImpl) {
        val project = editor.project ?: return
        JupyterKtScriptingSupport.getInstance(project).update()

        project.messageBus.connect(editor.disposable).subscribe(JupyterRuntimeService.Listener.TOPIC, object : JupyterRuntimeService.Listener {
            override fun sessionCreated(session: JupyterNotebookSession) {
                if (session.isKotlinNotebookSession()) {
                    val initCode = """
                        ${KotlinNotebookCodegen.generateSessionOptions(resolveSources = true, serializeScriptData = true)}
                        ${KotlinNotebookCodegen.generateColorSchemeChangeCode()}
                    """.trimIndent()

                    val mainExecutionCallback = session.virtualFile?.let { virtualFile ->
                        JupyterKotlinCellExecutionCallbackFactory.getInstance().createNotBoundCallback(
                            project,
                            virtualFile,
                            initCode
                        )
                    }

                    session.execute(
                        initCode,
                        onMessageCreated = {},
                        callbacks = listOfNotNull(
                            object : JupyterExecutionCallbackAdapter() {
                                override fun onExecuteReply(message: JupyterMessage) {
                                    LOG.debug(
                                        "Kotlin session has been initialized with response: ${message.json}"
                                    )
                                    EditorSessionInitializationService.getInstance().notifySessionInitialized(editor)
                                }
                            },
                            mainExecutionCallback
                        ),
                        silent = true,
                    )
                }
            }
        })
    }

    companion object {
        private val LOG = logger<KotlinNotebookEditorFactoryListener>()
    }
}