// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.openapi.project.ProjectCoreUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

fun findEnclosingProjectPathOrUseParent(virtualFile: VirtualFile): VirtualFile {
    val containingDir = if (virtualFile.isDirectory){
        virtualFile
    } else {
        virtualFile.parent
    }

    return findEnclosingProjectPath(containingDir)
        ?: containingDir
}

fun findEnclosingProjectPath(path: Path): VirtualFile? {
    var currentPath: Path? = path

    while (currentPath != null) {
        val virtualFile = VfsUtil.findFile(currentPath, true)
        if (virtualFile != null) {
            return findEnclosingProjectPath(virtualFile)
        }
        currentPath = currentPath.parent
    }

    return null
}

fun findEnclosingProjectPath(virtualFile: VirtualFile): VirtualFile? {
    var containingDirectory: VirtualFile? = virtualFile
    while (containingDirectory != null) {
        if (isProjectDirectory(containingDirectory)) break
        containingDirectory = containingDirectory.parent
    }
    return containingDirectory
}

private fun isProjectDirectory(virtualFile: VirtualFile): Boolean {
    return ProjectCoreUtil.isKnownProjectDirectory(virtualFile)
            || KotlinNotebookRootTypeInstance.rootVirtualFile == virtualFile
}
