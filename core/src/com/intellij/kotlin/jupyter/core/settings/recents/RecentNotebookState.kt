// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.recents

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.vfs.VirtualFile
import java.util.Objects
import javax.swing.Icon

data class RecentNotebook(
    val path: VirtualFile,
    val projectPath: VirtualFile,
    val timeStamp: Long = System.currentTimeMillis(),
)

data class RecentNotebookWithIcon(
    val notebook: RecentNotebook,
    val icon: Icon? = null,
)

class RecentNotebookState: BaseState() {
    var path: String? by string()
    var projectPath: String? by string()
    var timeStamp: String? by string(0.toString())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecentNotebookState) return false
        return Objects.equals(path, other.path)
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (path?.hashCode() ?: 0)
        return result
    }
}
