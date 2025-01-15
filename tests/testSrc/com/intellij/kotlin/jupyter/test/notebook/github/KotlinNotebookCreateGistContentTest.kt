// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.github

import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.jupyter.core.jackson
import com.intellij.kotlin.jupyter.test.KotlinNotebookBaseTestCase
import com.intellij.testFramework.TestDataPath
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.github.GithubGistContentsCollector
import org.junit.Test


@TestDataPath("\$CONTENT_ROOT/testData/notebooks")
class KotlinNotebookCreateGistContentTest : KotlinNotebookBaseTestCase() {
    @Test
    @TestMetadata("simple/singleEmptyCellNoCaret.ipynb")
    fun `gist contents of Jupyter notebook should be JSON, not raw text`() {
        configureByJupyterFile()
        val contents = GithubGistContentsCollector.collectContents(project, myFixture.editor, myFixture.file.virtualFile, null)
        contents.shouldBeSingleton {
            val notebookJson = jackson.readTree(it.content)
            val mimetypeNode = notebookJson["metadata"]["language_info"]["mimetype"]
            mimetypeNode.shouldBeTypeOf<TextNode>()
            mimetypeNode.asText() shouldBe "text/x-kotlin"
        }
    }
}
