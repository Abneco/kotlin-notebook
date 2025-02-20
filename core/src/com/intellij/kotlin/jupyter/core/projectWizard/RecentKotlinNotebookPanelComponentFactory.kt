package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.icons.AllIcons
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.kotlin.jupyter.core.language.JupyterKotlinFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.NotNull
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Insets
import java.awt.event.MouseEvent
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTree
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode

object RecentKotlinNotebookPanelComponentFactory {
    fun createComponent(parentDisposable: Disposable): JComponent {
        val panel = JBUI.Panels.simplePanel()
            .withBorder(JBUI.Borders.empty(13, 12))
            .withBackground(WelcomeScreenUIManager.getProjectsBackground())

        val tree = Tree()
        tree.background = WelcomeScreenUIManager.getProjectsBackground()
        tree.cellRenderer = NotebookTreeCellRenderer()
        val filteringTree = RecentKotlinNotebookFilteringTree(tree, parentDisposable)

        fun getCurrentRow(e: MouseEvent): Int {
            val point = e.point
            return TreeUtil.getRowForLocation(tree, point.x, point.y)
        }

        val mouseListener = object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1) {
                    val row = getCurrentRow(e)
                    if (row == -1) return
                    val path = tree.getPathForRow(row)
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    val item = node.userObject as? NotebookItem ?: return
                    val project = getOrCreateDefaultKotlinNotebookProject()
                    FileEditorManager.getInstance(project).openFile(item.file, true)
                }
            }

            override fun mouseMoved(e: MouseEvent) {
                val row = getCurrentRow(e)
                if (row != -1) {
                    if (!tree.isRowSelected(row)) {
                        tree.setSelectionRow(row)
                        // Repaint whole row to avoid flickering of row buttons
                        tree.repaint(tree.getRowBounds(row))
                    }

                    UIUtil.setCursor(tree, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
                }
                else {
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

        tree.putClientProperty(
            RenderingUtil.CUSTOM_SELECTION_BACKGROUND,
            Supplier { ListUiUtil.WithTallRow.background(JList<Any>(), isSelected = true, hasFocus = true) }
        )

        // Subscribe to file changes
        val connection = ApplicationManager.getApplication().messageBus.connect(parentDisposable)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val scratchService = ScratchFileService.getInstance()
                val scratchRoot = ScratchRootType.getInstance()
                if (events.any { event ->
                    val file = event.file
                    file != null && scratchService.getRootType(file) == scratchRoot
                }) {
                    SwingUtilities.invokeLater {
                        filteringTree.updateTree()
                    }
                }
            }
        })

        val northPanel = JBUI.Panels.simplePanel()
            .andTransparent()
            .withBorder(object : CustomLineBorder(WelcomeScreenUIManager.getSeparatorColor(), JBUI.insetsBottom(1)) {
                override fun getBorderInsets(c: Component): Insets {
                    return JBUI.insetsBottom(12)
                }
            })

        val searchField = filteringTree.installSearchField()
        if (ExperimentalUI.isNewUI()) {
            searchField.textEditor.putClientProperty("JTextField.Search.Icon", AllIcons.Actions.Search)
        }


        val createAction = CreateKotlinNotebookActionGroup()
        val group = DefaultActionGroup(createAction)
        val toolbar = object : ActionToolbarImpl(ActionPlaces.WELCOME_SCREEN, group, true) {
            override fun createToolbarButton(
                action: AnAction,
                look: ActionButtonLook?,
                place: String,
                presentation: Presentation,
                minimumSize: Supplier<out Dimension>
            ): ActionButton {
                presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
                return super.createToolbarButton(action, look, place, presentation, minimumSize)
            }
        }.apply {
            isOpaque = false
            setTargetComponent(searchField)
        }

        northPanel.add(searchField, BorderLayout.CENTER)
        northPanel.add(toolbar.component, BorderLayout.EAST)

        val scrollPane = ScrollPaneFactory.createScrollPane(
            tree,
            true
        ).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            isOpaque = false
            background = WelcomeScreenUIManager.getProjectsBackground()
        }

        val projectsPanel = JBUI.Panels.simplePanel(scrollPane)
            .andTransparent()
            .withBorder(JBUI.Borders.emptyTop(10))
            .withBackground(WelcomeScreenUIManager.getProjectsBackground())

        panel.add(northPanel, BorderLayout.NORTH)
        panel.add(projectsPanel, BorderLayout.CENTER)
        return panel
    }
}

private class NotebookTreeCellRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
        @NotNull tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ) {
        val node = value as? DefaultMutableTreeNode ?: return
        when (val item = node.userObject) {
            is NotebookItem -> {
                icon = JupyterKotlinFileType.icon
                append(item.file.name)
            }
            else -> {}
        }
    }
}

