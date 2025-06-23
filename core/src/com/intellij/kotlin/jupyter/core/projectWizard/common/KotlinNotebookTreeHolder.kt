// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard.common

import com.intellij.icons.AllIcons
import com.intellij.kotlin.jupyter.core.projectWizard.NotebookFileItem
import com.intellij.kotlin.jupyter.core.projectWizard.NotebookRootItem
import com.intellij.kotlin.jupyter.core.projectWizard.NotebookTreeNode
import com.intellij.kotlin.jupyter.core.projectWizard.RecentKotlinNotebooksService
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.settings.recents.RecentNotebookWithIcon
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.ui.render.RenderingHelper
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeCellRenderer

private class ActionsButton : SelectablePanel() {
    companion object {
        const val SIZE = 24
        const val RIGHT_GAP = 20
    }

    private val label = JLabel().apply {
        horizontalAlignment = SwingConstants.CENTER
        verticalAlignment = SwingConstants.CENTER
    }

    init {
        isOpaque = false
        preferredSize = Dimension(SIZE, SIZE)
        layout = BorderLayout()
        add(label, BorderLayout.CENTER)
        selectionArc = JBUI.scale(6)
    }

    fun setState(icon: Icon, hovered: Boolean) {
        label.icon = IconUtil.toSize(icon, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height)
        selectionColor = if (hovered) JBUI.CurrentTheme.List.buttonHoverBackground() else null
    }
}

private class NotebookActionButtonViewModel(
    var isButtonHovered: Boolean = false,
) {
    fun prepareActionsButton(button: ActionsButton, rowHovered: Boolean, icon: Icon, hoveredIcon: Icon) {
        val hovered = isButtonHovered && rowHovered
        button.isVisible = rowHovered
        button.setState(if (hovered) hoveredIcon else icon, hovered)
    }
}

private class NotebookComponent : JPanel(GridLayout()) {
    private val notebookNameLabel = JLabel()
    private val notebookPathLabel = ComponentPanelBuilder.createNonWrappingCommentComponent("").apply {
        foreground = NamedColorUtil.getInactiveTextColor()
    }
    private val notebookIconLabel = JLabel()
    private val notebookActions = ActionsButton().apply {
        setState(AllIcons.Ide.Notification.Gear, false)
    }
    private val notebookNamePanel = JPanel(VerticalLayout(4)).apply {
        isOpaque = false
        add(notebookNameLabel)
        add(notebookPathLabel)
    }

    init {
        border = JBUI.Borders.empty(4, 0)
        RowsGridBuilder(this)
            .cell(notebookIconLabel,
                  gaps = if (ExperimentalUI.isNewUI()) UnscaledGaps(6, 0, 0, 8) else UnscaledGaps(top = 8, right = 8),
                  verticalAlign = VerticalAlign.TOP)
            .cell(notebookNamePanel, resizableColumn = true, horizontalAlign = HorizontalAlign.FILL, gaps = UnscaledGaps(4, 4, 4, 4))
            .cell(notebookActions, gaps = UnscaledGaps(right = ActionsButton.RIGHT_GAP))
    }

    fun customizeComponent(notebookWithIcon: RecentNotebookWithIcon, rowHovered: Boolean, buttonViewModel: NotebookActionButtonViewModel): JComponent {
        val notebookPath = notebookWithIcon.notebook.path
        notebookNameLabel.text = notebookPath.name
        notebookPathLabel.text = FileUtil.getLocationRelativeToUserHome(notebookPath.parent.path)

        val icon = notebookWithIcon.icon
        if (icon != null) {
            notebookIconLabel.icon = IconUtil.resizeSquared(icon, 24)
        }

        buttonViewModel.prepareActionsButton(notebookActions, rowHovered, AllIcons.Ide.Notification.Gear, AllIcons.Ide.Notification.GearHover)

        return this
    }
}

internal class KotlinNotebookTreeHolder {
    private val tree = Tree()
    private val treeModel = DefaultTreeModel(NotebookTreeNode(NotebookRootItem(emptyList())))
    private val buttonViewModel = NotebookActionButtonViewModel()
    private val notebookComponent = NotebookComponent()

    init {
        tree.model = treeModel
        tree.isRootVisible = false
        tree.rowHeight = 0
        tree.border = JBUI.Borders.empty(4, 0)
        tree.emptyText.text = KotlinNotebookBundle.message("kotlin.notebook.no.recent.notebooks.found")
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
        tree.cellRenderer = object : TreeCellRenderer {
            override fun getTreeCellRendererComponent(
                tree: JTree,
                value: Any?,
                selected: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ): Component {
                val node = value as? NotebookTreeNode ?: return JLabel()
                val item = node.item

                if (item is NotebookFileItem) {
                    return notebookComponent.customizeComponent(
                        item.notebookWithIcon,
                        selected,
                        buttonViewModel
                    )
                }

                return JLabel(item.displayName())
            }
        }
    }

    private fun setupMouseListener() {
        val actionGroup = ActionManager.getInstance().getAction("KotlinNotebookActionGroup") as ActionGroup
        val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.WELCOME_SCREEN, actionGroup)

        val mouseListener = object : java.awt.event.MouseAdapter() {
            private fun getCurrentRow(e: MouseEvent): Int {
                val point = e.point
                return TreeUtil.getRowForLocation(tree, point.x, point.y)
            }

            private fun intersectWithActionIcon(point: Point): Boolean {
                val row = TreeUtil.getRowForLocation(tree, point.x, point.y)
                if (row == -1) return false
                return getActionsButtonRect(row).contains(point)
            }

            private fun getActionsButtonRect(row: Int): Rectangle {
                val helper = RenderingHelper(tree)
                val bounds = tree.getRowBounds(row)
                val size = JBUI.scale(ActionsButton.SIZE)

                val node = tree.getPathForRow(row)?.lastPathComponent as? NotebookTreeNode ?: return Rectangle()
                val item = node.item

                if (item !is NotebookFileItem) return Rectangle()

                val rightGap = JBUI.scale(ActionsButton.RIGHT_GAP)

                return Rectangle(
                    helper.width - helper.rightMargin - size - rightGap,
                    bounds.y + (bounds.height - size) / 2, 
                    size, 
                    size
                )
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isConsumed) return

                if (e.clickCount == 1 && e.button == MouseEvent.BUTTON1) {
                    val point = e.point
                    val treePath = TreeUtil.getPathForLocation(tree, point.x, point.y) ?: return
                    val node = treePath.lastPathComponent as? NotebookTreeNode ?: return
                    val item = node.item as? NotebookFileItem ?: return

                    if (intersectWithActionIcon(point)) {
                        val dataContext = SimpleDataContext.builder()
                            .add(RECENT_NOTEBOOK_KEY, item.notebookWithIcon.notebook)
                            .add(NOTEBOOK_TREE_HOLDER_KEY, this@KotlinNotebookTreeHolder)
                            .build()

                        popupMenu.setDataContext { dataContext }
                        popupMenu.component.show(e.component, e.x, e.y)
                    } else {
                        openNotebook(item.notebookWithIcon.notebook)
                    }

                    e.consume()
                }
            }

            override fun mouseMoved(e: MouseEvent) {
                if (popupMenu.component.isVisible) return

                val row = getCurrentRow(e)
                if (row != -1) {
                    if (!tree.isRowSelected(row)) {
                        tree.setSelectionRow(row)
                        tree.repaint(tree.getRowBounds(row))
                    }
                    UIUtil.setCursor(tree, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
                    buttonViewModel.isButtonHovered = intersectWithActionIcon(e.point)
                } else {
                    UIUtil.setCursor(tree, Cursor.getDefaultCursor())
                    tree.clearSelection()
                    buttonViewModel.isButtonHovered = false
                }
            }

            override fun mouseExited(e: MouseEvent) {
                if (popupMenu.component.isVisible) return

                tree.clearSelection()
                buttonViewModel.isButtonHovered = false
            }
        }

        tree.addMouseListener(mouseListener)
        tree.addMouseMotionListener(mouseListener)
    }

    fun getTree(): Tree = tree
    fun getRoot(): NotebookTreeNode = treeModel.root as NotebookTreeNode

    fun updateAsync(): Job {
        return KotlinNotebookPluginScope.global.launch(Dispatchers.Default) {
            val files = RecentKotlinNotebooksService.getInstance().getNotebooksWithIcons()
            withContext(Dispatchers.EDT) {
                val root = NotebookTreeNode(NotebookRootItem(files))
                treeModel.setRoot(root)
            }
        }
    }

    fun revalidateTree() {
        tree.model = treeModel
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
