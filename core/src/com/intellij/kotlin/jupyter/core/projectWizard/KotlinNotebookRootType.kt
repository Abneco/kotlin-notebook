// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.ide.scratch.RootType
import com.intellij.ide.scratch.RootType.findByClass
import com.intellij.ide.scratch.ScratchFileTypeIcon
import com.intellij.kotlin.jupyter.core.language.JupyterKotlinFileType
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.absolutePathString


class KotlinNotebookRootType : RootType("kotlinNotebook", KotlinNotebookBundle.message("kotlin.notebook.root.type.display.name")) {
    private val notebookNameSuffix by lazy {
        "." + JupyterKotlinFileType.defaultExtension.lowercase()
    }

    init {
        registerRootDirectory()
    }

    override fun patchIcon(baseIcon: Icon, file: VirtualFile, flags: Int, project: Project?): Icon {
        if (file.isDirectory()) return baseIcon
        if (project?.name == DefaultKotlinNotebookProject.NAME) return baseIcon
        return ScratchFileTypeIcon(baseIcon)
    }

    override fun containsFile(file: VirtualFile?): Boolean {
        if (file == null) return false
        if (isHidden) return false

        // Get the root path where notebooks should be located
        val rootPath = DefaultKotlinNotebookProject.rootPath
        val filePath = Path.of(file.path)

        // Check if the file is under our root directory
        return filePath.startsWith(rootPath)
    }

    override fun isIgnored(project: Project, element: VirtualFile): Boolean {
        if (!element.isFile) return true
        if (project.name == DefaultKotlinNotebookProject.NAME) return true
        return !element.name.endsWith(notebookNameSuffix)
    }

    override fun isHidden(): Boolean = !kotlinNotebookWelcomeFeaturesEnabled

    private fun registerRootDirectory() {
        if (!isHidden) {
            val propertyName = PathManager.PROPERTY_SCRATCH_PATH + "/" + id
            if (System.getProperty(propertyName) != null) return
            System.setProperty(propertyName, DefaultKotlinNotebookProject.rootPath.absolutePathString())
        }
    }
}

val KotlinNotebookRootTypeInstance: KotlinNotebookRootType
    get() = findByClass(KotlinNotebookRootType::class.java)
