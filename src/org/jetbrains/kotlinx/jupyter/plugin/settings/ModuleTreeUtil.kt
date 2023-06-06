// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.ide.projectView.impl.ModuleGroup
import com.intellij.ide.projectView.impl.ModuleGroupingTreeHelper
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleGrouper
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTree.CheckboxTreeCellRenderer
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.PlatformIcons
import com.intellij.util.applyIf
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel

internal fun buildModuleTree(project: Project, initialModules: Set<Module>): CheckboxTree {
    val moduleGrouper = ModuleGrouper.instanceFor(project)
    val rootNode = CheckedTreeNode(null)

    val tree = object : CheckboxTree(
        ModuleCheckboxRenderer(moduleGrouper), rootNode,
        CheckPolicy(true, true, false, false)
    ) {
        override fun setEnabled(enabled: Boolean) {
            super.setEnabled(enabled)
            TreeUtil.treeNodeTraverser(rootNode).traverse().forEach {
                (it as? CheckedTreeNode)?.isEnabled = enabled
            }
        }
    }

    val grouping = ModuleGroupingTreeHelper.createDefaultGrouping(moduleGrouper)
    val treeHelper = ModuleGroupingTreeHelper.forEmptyTree(true,
                                                           grouping,
                                                           { CheckedTreeNode(it) },
                                                           { module ->
                                                               CheckedTreeNode(module).also {
                                                                   it.isChecked = initialModules.contains(module)
                                                               }
                                                           },
                                                           compareBy(NaturalComparator.INSTANCE) { it.userObject.toString() })
    treeHelper.createModuleNodes(getSuitableModules(project), rootNode, tree.model as DefaultTreeModel)

    return tree
}

private class ModuleCheckboxRenderer(private val moduleGrouper: ModuleGrouper) : CheckboxTreeCellRenderer() {
    override fun customizeRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ) {
        val checkedTreeNode = value as? CheckedTreeNode ?: return

        val isEnabled = checkedTreeNode.isEnabled
        textRenderer.isEnabled = isEnabled

        val userObject = checkedTreeNode.userObject ?: return
        when (userObject) {
            is Module -> {
                textRenderer.append(moduleGrouper.getShortenedName(userObject), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                textRenderer.icon = ModuleType.get(userObject).icon.applyIf(!isEnabled) { IconLoader.getDisabledIcon(this) }
            }
            is ModuleGroup -> {
                textRenderer.append(userObject.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                textRenderer.icon = PlatformIcons.CLOSED_MODULE_GROUP_ICON.applyIf(!isEnabled) { IconLoader.getDisabledIcon(this) }
            }
        }
        SpeedSearchUtil.applySpeedSearchHighlighting(tree, textRenderer, true, selected)
    }
}

internal inline fun <reified T> CheckboxTree.getSelectedItems(): List<T> {
    return getCheckedNodes(T::class.java, null)?.toList() ?: emptyList()
}