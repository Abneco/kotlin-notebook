// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.refactorings

import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterServers
import com.intellij.jupyter.core.jupyter.editor.JupyterDSFileEditorProvider
import com.intellij.kotlin.jupyter.test.KotlinNotebookBaseTestCase
import com.intellij.kotlin.jupyter.test.runWithJupyterSession
import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.notebooks.ui.editor.actions.command.mode.setMode
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.notebooks.tests.configureByJupyterFile

abstract class RefactoringTestBase(private val refactoringActionId: String, testDataPath: String) : KotlinNotebookBaseTestCase(testDataPath) {
    override lateinit var originalVirtualFile: VirtualFile

    override fun runInDispatchThread(): Boolean {
        return false
    }

    override fun setUp() {
        super.setUp()
        Disposer.register(testRootDisposable, JupyterServers.getInstance())
    }

    protected fun doTest(caretInitializer: (CaretModel) -> Unit) {
        myFixture.setCaresAboutInjection(true)
        val editorProvider = FileEditorProvider.EP_FILE_EDITOR_PROVIDER.findExtension(JupyterDSFileEditorProvider::class.java)!!
        val notebookFile = myFixture.configureByJupyterFile("${getTestName(true)}.ipynb", testDataPath, fileEditorProvider = editorProvider)
        originalVirtualFile = notebookFile.file

        val psiFile = invokeAndWaitIfNeeded {
            myFixture.editor.setMode(NotebookEditorMode.EDIT)

            val caretModel = myFixture.editor.caretModel
            caretInitializer(caretModel)

            PsiManager.getInstance(project).findFile(originalVirtualFile)!!
        }

        doEditorActionWithSession(psiFile)

        myFixture.checkResultByFile("${getTestName(true)}.txt", true)
    }

    private fun doEditorActionWithSession(psiFile: PsiFile) {
        runWithJupyterSession(psiFile) {
            setUpDependenciesSynchronously(emptyList())
            myFixture.performEditorAction(refactoringActionId)

            invokeAndWaitIfNeeded {
                NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
            }
        }
    }
}
