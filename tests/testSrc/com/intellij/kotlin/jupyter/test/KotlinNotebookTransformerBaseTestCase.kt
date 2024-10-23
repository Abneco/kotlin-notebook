// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.intellij.injected.editor.DocumentWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.notebooks.ui.editor.actions.command.mode.setMode
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.notebooks.tests.configureByJupyterFile

abstract class KotlinNotebookTransformerBaseTestCase(testDataPath: String) : KotlinNotebookBaseTestCase(testDataPath) {
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

    protected fun doSimpleTransformerTest(
        expectedDocumentText: String,
        testOptions: TestOptions = TestOptions.DEFAULT,
        notebookFactory: () -> BackedNotebookVirtualFile = {
            myFixture.configureByJupyterFile("${getTestName(true)}.ipynb", testDataPath)
        },
        transformer: () -> Unit
    ) {
        myFixture.setCaresAboutInjection(testOptions.caresAboutInjection)
        _notebookFile = notebookFactory()
      invokeAndWaitIfNeeded {
        myFixture.editor.setMode(NotebookEditorMode.EDIT)
      }
        originalVirtualFile = myFixture.file.virtualFile
      setUpScriptingDependencies(myFixture)

        if (!testOptions.caresAboutInjection) {
            // Cache injection on current offset
            InjectedLanguageManager.getInstance(project).findInjectedElementAt(myFixture.file, myFixture.caretOffset)
        }
        transformer()

        val doc = myFixture.editor.document
        val docToCheck = if (testOptions.checkTopLevelDocument && doc is DocumentWindow) {
            doc.delegate
        } else {
            doc
        }

        val actualText = runReadAction {
          docToCheck.text
        }.replace("\r\n", "\n")
      assertEquals(expectedDocumentText, actualText)
    }
}