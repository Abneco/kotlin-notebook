// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.refactorings

import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterKtScriptingSupport
import org.jetbrains.kotlinx.jupyter.plugin.test.KotlinNotebookBaseTestCase
import org.jetbrains.plugins.notebooks.jupyter.configureByJupyterFile
import com.intellij.jupyter.core.jupyter.editor.JupyterDSFileEditorProvider
import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.notebooks.ui.editor.actions.command.mode.setMode

abstract class RefactoringTestBase(private val refactoringActionId: String) : KotlinNotebookBaseTestCase() {
    override lateinit var originalVirtualFile: VirtualFile

    override fun runInDispatchThread(): Boolean {
        return false
    }

    protected fun doTest(caretInitializer: (CaretModel) -> Unit) {
        myFixture.setCaresAboutInjection(true)
        val editorProvider = FileEditorProvider.EP_FILE_EDITOR_PROVIDER.findExtension(JupyterDSFileEditorProvider::class.java)!!
        val notebookFile = myFixture.configureByJupyterFile("${getTestName(true)}.ipynb", testDataPath, fileEditorProvider = editorProvider)
        originalVirtualFile = notebookFile.file

        invokeAndWaitIfNeeded {
            myFixture.editor.setMode(NotebookEditorMode.EDIT)

            val caretModel = myFixture.editor.caretModel
            caretInitializer(caretModel)
        }

        JupyterKtScriptingSupport.updateSynchronously(project)

        myFixture.performEditorAction(refactoringActionId)

        invokeAndWaitIfNeeded {
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        }

        myFixture.checkResultByFile("${getTestName(true)}.txt", true)
    }
}
