// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.refactorings

import com.intellij.jupyter.core.jupyter.editor.JupyterDSFileEditorProvider
import com.intellij.kotlin.jupyter.core.util.findPsiFile
import com.intellij.kotlin.jupyter.test.KotlinNotebookBaseTestCase
import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.notebooks.ui.editor.actions.command.mode.setMode
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

abstract class RefactoringTestBase(private val refactoringActionId: String) : KotlinNotebookBaseTestCase() {
    override lateinit var originalVirtualFile: VirtualFile

    override fun runInDispatchThread(): Boolean {
        return false
    }

    protected fun doTest(caretInitializer: (CaretModel) -> Unit) {
        myFixture.setCaresAboutInjection(true)
        setUpProjectSdkIfNeeded()
        val editorProvider = FileEditorProvider.EP_FILE_EDITOR_PROVIDER.findExtension(JupyterDSFileEditorProvider::class.java)!!
        val notebookFile = configureByJupyterFile(fileEditorProvider = editorProvider)
        originalVirtualFile = notebookFile.file

        val psiFile = invokeAndWaitIfNeeded {
            myFixture.editor.setMode(NotebookEditorMode.EDIT)

            val caretModel = myFixture.editor.caretModel
            caretInitializer(caretModel)

            originalVirtualFile.findPsiFile(project)!!
        }

        doEditorActionWithSession(psiFile)
        val expectedFile = getTestFile(".txt").relativeTo(Path(testDataPath))

        myFixture.checkResultByFile(expectedFile.pathString, true)
    }

    private fun doEditorActionWithSession(psiFile: PsiFile) {
        doTestWithJupyterSessionAndBaseDependencies(psiFile) {
            myFixture.performEditorAction(refactoringActionId)

            invokeAndWaitIfNeeded {
                NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
            }
        }
    }
}
