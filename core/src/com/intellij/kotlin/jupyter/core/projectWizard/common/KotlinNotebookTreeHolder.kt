// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard.common

import com.intellij.kotlin.jupyter.core.language.JupyterKotlinFileType
import com.intellij.kotlin.jupyter.core.projectWizard.NotebookFileItem
import com.intellij.kotlin.jupyter.core.projectWizard.NotebookRootItem
import com.intellij.kotlin.jupyter.core.projectWizard.NotebookTreeNode
import com.intellij.kotlin.jupyter.core.projectWizard.RecentKotlinNotebooksService
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.openapi.application.EDT
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Cursor
import java.awt.event.MouseEvent
import java.util.function.Supplier
import javax.swing.JList
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.ScrollPaneConstants
import javax.swing.tree.DefaultTreeModel

class KotlinNotebookTreeHolder(
    private val onItemClick: (VirtualFile) -> Unit
) {
    private val tree = Tree()
    private val treeModel = DefaultTreeModel(NotebookTreeNode(NotebookRootItem(emptyList())))

    init {
        tree.model = treeModel
        tree.isRootVisible = false
        tree.rowHeight = 0
        tree.border = JBUI.Borders.empty()
        setupTreeProperties()
        setupTreeRenderer()
        setupMouseListener()
    }

    private fun setupTreeProperties() {
        tree.background = WelcomeScreenUIManager.getProjectsBackground()
        tree.putClientProperty(
            RenderingUtil.CUSTOM_SELECTION_BACKGROUND,
            Supplier { ListUiUtil.WithTallRow.background(JList<Any>(), isSelected = true, hasFocus = true) }
        )
    }

    private fun setupTreeRenderer() {
        tree.cellRenderer = object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(
                tree: JTree,
                value: Any?,
                selected: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ) {
                val node = value as? NotebookTreeNode ?: return
                val item = node.item
                if (item is NotebookFileItem) {
                    icon = JupyterKotlinFileType.icon
                }

                append(item.displayName())
            }
        }
    }

    private fun setupMouseListener() {
        val mouseListener = object : java.awt.event.MouseAdapter() {
            private fun getCurrentRow(e: MouseEvent): Int {
                val point = e.point
                return TreeUtil.getRowForLocation(tree, point.x, point.y)
            }

            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1 && e.button == MouseEvent.BUTTON1) {
                    val row = getCurrentRow(e)
                    if (row == -1) return
                    val path = tree.getPathForRow(row)
                    val node = path.lastPathComponent as? NotebookTreeNode ?: return
                    val item = node.item as? NotebookFileItem ?: return
                    onItemClick(item.file)
                }
            }

            override fun mouseMoved(e: MouseEvent) {
                val row = getCurrentRow(e)
                if (row != -1) {
                    if (!tree.isRowSelected(row)) {
                        tree.setSelectionRow(row)
                        tree.repaint(tree.getRowBounds(row))
                    }
                    UIUtil.setCursor(tree, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
                } else {
                    UIUtil.setCursor(tree, Cursor.getDefaultCursor())
                    tree.clearSelection()
                }
            }

            override fun mouseExited(e: MouseEvent) {
                tree.clearSelection()
            }
        }

        tree.addMouseListener(mouseListener)
        tree.addMouseMotionListener(mouseListener)
    }

    fun getTree(): Tree = tree
    fun getRoot(): NotebookTreeNode = treeModel.root as NotebookTreeNode

    fun updateAsync(): Job {
        return KotlinNotebookPluginScope.global.launch(Dispatchers.Default) {
            val files = RecentKotlinNotebooksService.getInstance().getNotebooks()
            withContext(Dispatchers.EDT) {
                val root = NotebookTreeNode(NotebookRootItem(files))
                treeModel.setRoot(root)
            }
        }
    }

    fun createScrollPane(): JScrollPane {
        return ScrollPaneFactory.createScrollPane(
            tree,
            true
        ).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            isOpaque = false
            background = WelcomeScreenUIManager.getProjectsBackground()
            border = JBUI.Borders.empty()
        }
    }
}
