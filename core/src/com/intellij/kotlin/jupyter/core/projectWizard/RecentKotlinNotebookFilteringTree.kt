// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.kotlin.jupyter.core.projectWizard.common.KotlinNotebookTreeHolder
import com.intellij.kotlin.jupyter.core.projectWizard.common.openNotebook
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.ui.addKeyboardAction
import com.intellij.ui.FilteringTree
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.util.asSafely
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

internal class RecentKotlinNotebookFilteringTree(
    private val treeComponent: KotlinNotebookTreeHolder
) : FilteringTree<NotebookTreeNode, NotebookItem>(
    treeComponent.getTree(),
    treeComponent.getRoot()
) {
    override fun getNodeClass(): Class<NotebookTreeNode> = NotebookTreeNode::class.java

    override fun getText(item: NotebookItem?): String = item?.searchName().orEmpty()

    override fun getChildren(item: NotebookItem): Iterable<NotebookItem> = item.children()

    override fun createNode(item: NotebookItem): NotebookTreeNode = NotebookTreeNode(item)

    suspend fun update() {
        treeComponent.update()
        searchModel.updateStructure()
        TreeUtil.expandAll(tree)
    }

    override fun installSearchField(): SearchTextField {
        return super.installSearchField().apply {
            isOpaque = false
            border = JBUI.Borders.empty()

            textEditor.apply {
                isOpaque = false
                border = JBUI.Borders.empty()

                val fieldText = KotlinNotebookBundle.message("kotlin.notebook.welcome.screen.search")
                emptyText.text = fieldText
                accessibleContext.accessibleName = fieldText

                TextComponentEmptyText.setupPlaceholderVisibility(this)
                addKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)) { activateItems() }
            }
        }
    }

    private fun activateItems() {
        tree.selectionModel.selectionPaths.mapNotNull {
            it.lastPathComponent.asSafely<NotebookTreeNode>()
        }.forEach { node ->
            val item = node.userObject.asSafely<NotebookItem>() ?: return
            activateItem(item)
        }
    }

    private fun activateItem(item: NotebookItem) {
        when (item) {
            is NotebookFileItem -> {
                openNotebook(item.notebookWithIcon.notebook)
            }
            is NotebookRootItem -> {}
        }
    }
}
