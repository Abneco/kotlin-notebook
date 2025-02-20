// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.Disposable
import com.intellij.ui.FilteringTree
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.tree.DefaultMutableTreeNode

class RecentKotlinNotebookFilteringTree(
    treeComponent: Tree,
    parentDisposable: Disposable
) : FilteringTree<DefaultMutableTreeNode, NotebookTreeItem>(
    treeComponent,
    DefaultMutableTreeNode(RootItem(RecentKotlinNotebooksService.getInstance().getNotebooks()))
) {
    init {
        treeComponent.isRootVisible = false
        treeComponent.rowHeight = 0
        searchModel.updateStructure()
    }

    override fun getNodeClass(): Class<DefaultMutableTreeNode> = DefaultMutableTreeNode::class.java

    override fun getText(item: NotebookTreeItem?): String = when (item) {
        is NotebookItem -> item.searchName()
        else -> item?.displayName().orEmpty().lowercase()
    }

    override fun getChildren(item: NotebookTreeItem): Iterable<NotebookTreeItem> = item.children()

    override fun createNode(item: NotebookTreeItem): DefaultMutableTreeNode = DefaultMutableTreeNode(item)

    fun updateTree() {
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

            }
        }
    }
}
