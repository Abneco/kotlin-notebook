// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.refactorings

import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.fileEditor.FileEditorProvider
import org.jetbrains.kotlinx.jupyter.plugin.test.KotlinNotebookBaseTestCase
import org.jetbrains.plugins.notebooks.jupyter.editor.JupyterDSFileEditorProvider

abstract class RefactoringTestBase(private val refactoringActionId: String) : KotlinNotebookBaseTestCase() {
    override fun runInDispatchThread(): Boolean {
        return false
    }

    protected fun doTest(caretInitializer: (CaretModel) -> Unit) {
        val editorProvider = FileEditorProvider.EP_FILE_EDITOR_PROVIDER.findExtension(JupyterDSFileEditorProvider::class.java)!!
        configureTestDependencies(
            caresAboutInjection = true,
            fileEditorProvider = editorProvider
        )

        invokeAndWaitIfNeeded {
            val caretModel = myFixture.editor.caretModel
            caretInitializer(caretModel)
        }

        myFixture.performEditorAction(refactoringActionId)

        invokeAndWaitIfNeeded {
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        }

        myFixture.checkResultByFile("${getTestName(true)}.txt", true)
    }
}
