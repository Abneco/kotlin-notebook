// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.intellij.injected.editor.DocumentWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.util.findPsiFile
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.notebooks.ui.editor.actions.command.mode.setMode
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.PsiDocumentManagerBase

abstract class KotlinNotebookTransformerBaseTestCase : KotlinNotebookBaseTestCase() {
    override lateinit var originalVirtualFile: VirtualFile

    override val notebookFile: BackedNotebookVirtualFile get() = _notebookFile!!
    private var _notebookFile: BackedNotebookVirtualFile? = null

    protected class TestOptions(
        val checkTopLevelDocument: Boolean = false,
        val caresAboutInjection: Boolean = true,
    ) {
        companion object {
            val DEFAULT = TestOptions()
        }
    }

    /**
     * This test should not be on EDT as they are using [setUpDependenciesSynchronously],
     * which would block EDT otherwise.
     */
    override fun runInDispatchThread(): Boolean {
        return false
    }

    protected fun doSimpleTransformerTest(
        expectedDocumentText: String,
        testOptions: TestOptions = TestOptions.DEFAULT,
        transformer: () -> Unit
    ) {
        myFixture.setCaresAboutInjection(testOptions.caresAboutInjection)

        val notebookPsiFile = invokeAndWaitIfNeeded {
            _notebookFile = configureByJupyterFile()
            myFixture.editor.setMode(NotebookEditorMode.EDIT)
            _notebookFile?.file?.findPsiFile(project)!!
        }
        originalVirtualFile = myFixture.file.virtualFile

        if (!testOptions.caresAboutInjection) {
            // Cache injection on current offset
            ReadAction.run<Throwable> {
                InjectedLanguageManager.getInstance(project).findInjectedElementAt(myFixture.file, myFixture.caretOffset)
            }
        }

        doTestWithJupyterSessionAndBaseDependencies(notebookPsiFile) {
            transformer()
        }

        val doc = myFixture.editor.document
        val docToCheck = if (testOptions.checkTopLevelDocument) {
            PsiDocumentManagerBase.getTopLevelDocument(doc)
        } else {
            doc
        }

        val actualText = ReadAction.compute<String, Throwable> {
            docToCheck.text
        }.replace("\r\n", "\n")
        assertEquals(expectedDocumentText, actualText)
    }
}