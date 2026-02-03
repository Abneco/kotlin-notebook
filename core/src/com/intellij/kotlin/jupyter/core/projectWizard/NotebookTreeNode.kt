// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import java.util.Vector
import javax.swing.tree.DefaultMutableTreeNode

class NotebookTreeNode(val item: NotebookItem): DefaultMutableTreeNode() {
    init {
        userObject = item
        if (item is NotebookRootItem) {
            children = Vector(item.children().map { NotebookTreeNode(it) })
        }
    }
}


