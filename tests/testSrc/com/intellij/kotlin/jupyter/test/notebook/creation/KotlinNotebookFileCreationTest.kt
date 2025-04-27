// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.creation

import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.ide.scratch.ScratchFileActions
import com.intellij.ide.scratch.ScratchFileCreationHelper
import com.intellij.jupyter.core.jupyter.helper.NotebookLanguageDetector
import com.intellij.jupyter.core.jupyter.helper.getGetOrReadJupyterNotebook
import com.intellij.kotlin.jupyter.core.language.JupyterKotlinFileType
import com.intellij.kotlin.jupyter.test.KotlinNotebookBaseTestCase
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiFile
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterFile
import org.junit.Test

class KotlinNotebookFileCreationTest : KotlinNotebookBaseTestCase() {
    @Test
    fun `scratch file is created successfully`() = doTest(createFile = {
        invokeAndWaitIfNeeded {
            ScratchFileActions.doCreateNewScratch(
                myFixture.project,
                createKotlinNotebookScratchContext()
            )
        }
    }) { psiFile ->
        psiFile.shouldBeTypeOf<JupyterFile>()
        NotebookLanguageDetector.detectLanguage(psiFile.virtualFile) shouldBe KotlinLanguage.INSTANCE
        psiFile.text shouldBe "#%%\n"

        val notebookJson = psiFile.virtualFile.getGetOrReadJupyterNotebook().json
        val mimetypeNode = notebookJson["metadata"]["language_info"]["mimetype"]
        mimetypeNode.shouldBeTypeOf<TextNode>()
        mimetypeNode.asText() shouldBe "text/x-kotlin"
    }

    private fun doTest(
        createFile: () -> PsiFile?,
        assertFile: (PsiFile) -> Unit
    ) {
        val psiFile = createFile()
        psiFile.shouldNotBeNull()
        try {
            assertFile(psiFile)
        } finally {
            val virtualFile = psiFile.virtualFile
            WriteCommandAction.runWriteCommandAction(project) {
                virtualFile.delete(null)
            }
        }
    }

    private fun createKotlinNotebookScratchContext(): ScratchFileCreationHelper.Context {
        return ScratchFileCreationHelper.Context().apply {
            language = JupyterKotlinFileType.language
            fileExtension = JupyterKotlinFileType.getDefaultExtension()
        }
    }
}