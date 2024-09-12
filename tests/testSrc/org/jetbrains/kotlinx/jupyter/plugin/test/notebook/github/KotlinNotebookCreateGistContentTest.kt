// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.github

import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.jupyter.core.jackson
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import org.jetbrains.kotlinx.jupyter.plugin.test.KotlinNotebookBaseTestCase
import org.jetbrains.kotlinx.jupyter.plugin.test.configureBySingleEmptyCellNoCaretNotebook
import org.jetbrains.plugins.github.GithubGistContentsCollector
import org.jetbrains.plugins.notebooks.tests.SingleFileImplRule
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4


@RunWith(JUnit4::class)
class KotlinNotebookCreateGistContentTest: KotlinNotebookBaseTestCase() {
    companion object {
        @get:ClassRule
        @JvmStatic
        val singleFileModeRule = SingleFileImplRule(true)
    }

    @Test
    fun `gist contents of Jupyter notebook should be JSON, not raw text`() {
        myFixture.configureBySingleEmptyCellNoCaretNotebook(copyToProject = true)
        val contents = GithubGistContentsCollector.collectContents(project, myFixture.editor, myFixture.file.virtualFile, null)
        contents.shouldBeSingleton {
            val notebookJson = jackson.readTree(it.content)
            val mimetypeNode = notebookJson["metadata"]["language_info"]["mimetype"]
            mimetypeNode.shouldBeTypeOf<TextNode>()
            mimetypeNode.asText() shouldBe "text/x-kotlin"
        }
    }
}
