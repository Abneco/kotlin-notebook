// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.github

import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.jupyter.core.jackson
import com.intellij.jupyter.core.jupyter.helper.getOrReadJupyterNotebook
import com.intellij.kotlin.jupyter.test.KotlinNotebookTestCase
import com.intellij.testFramework.TestDataPath
import io.kotest.matchers.collections.shouldContainNoNulls
import io.kotest.matchers.collections.shouldContainOnlyNulls
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.Test


@TestDataPath($$"$CONTENT_ROOT/testData/notebooks")
class KotlinNotebookCreateGistContentTest : KotlinNotebookTestCase() {
    @Test
    @TestMetadata("simple/singleEmptyCellNoCaret.ipynb")
    fun `gist contents of Jupyter notebook should be JSON, not raw text`() = runNotebookTest {
        exportAsGist().let { gist ->
            val notebookJson = jackson.readTree(gist.content)
            val mimetypeNode = notebookJson["metadata"]["language_info"]["mimetype"]
            mimetypeNode.shouldBeTypeOf<TextNode>()
            mimetypeNode.asText() shouldBe "text/x-kotlin"
        }
    }


    @Test
    @TestMetadata("gist/nbformat_4_0_with_cell_id.ipynb")
    fun `cell IDs should be removed from the notebook with schema version below 4_5`() = runNotebookTest {
        val notebook = notebookFile.virtualFile.getOrReadJupyterNotebook()
        with(getCellIds(notebook.json)) {
            shouldHaveSize(10)
            shouldContainNoNulls()
        }

        val gist = exportAsGist()
        val exportedNotebookJson = jackson.readTree(gist.content).shouldBeTypeOf<ObjectNode>()
        with(getCellIds(exportedNotebookJson)) {
            shouldHaveSize(10)
            shouldContainOnlyNulls()
        }
    }

    @Test
    @TestMetadata("gist/nbformat_4_5_without_cell_id.ipynb")
    fun `cell IDs should be added to the notebook with schema version of 4_5 and above`() = runNotebookTest {
        val notebook = notebookFile.virtualFile.getOrReadJupyterNotebook()
        with(getCellIds(notebook.json)) {
            shouldHaveSize(3)
            // IDs are added when the notebook is loaded
            shouldContainNoNulls()
        }

        val gist = exportAsGist()
        val exportedNotebookJson = jackson.readTree(gist.content).shouldBeTypeOf<ObjectNode>()
        with(getCellIds(exportedNotebookJson)) {
            shouldHaveSize(3)
            shouldContainNoNulls()
        }
    }

    private fun getCellIds(notebookJson: ObjectNode): List<String?> {
        return notebookJson["cells"]
            .map {
                when(val node = it["id"]) {
                    is TextNode -> node.asText()
                    is NullNode, null -> null
                    else -> io.kotest.assertions.fail("Unexpected cell ID type: ${node.javaClass.name}")
                }
            }
    }
}
