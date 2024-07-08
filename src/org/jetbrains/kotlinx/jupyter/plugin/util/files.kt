package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.content.ContentManager
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlin.idea.util.sourceRoots
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.core.impl.file.originFile
import java.io.File
import java.nio.file.Path

val File.isNotEmptyDirectory: Boolean
    get() = exists() && isDirectory && (list()?.isNotEmpty() ?: false)

fun Project.allSourceRoots(): List<File> {
    val moduleManager = ModuleManager.getInstance(this)
    val allModules = moduleManager.modules
    return allModules.flatMap { module ->
        module.sourceRoots.map { File(it.path) }
    }
}

fun Path.findNotebookVirtualFileOrNull(): BackedNotebookVirtualFile? {
    val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(this) ?: return null
    return BackedNotebookVirtualFile.find(virtualFile)
}

val VirtualFile.parentsWithSelf: Sequence<VirtualFile> get() = generateSequence(this) { it.parent }

typealias ProjectArtifacts = List<String>

fun VirtualFile.toAbsolutePath(): Path {
    return File(path).absoluteFile.toPath()
}

@NlsSafe
@RequiresBackgroundThread
internal fun BackedNotebookVirtualFile.toPresentablePathAsTabTitle(
    project: Project,
    contentManager: ContentManager
): String {
    ThreadingAssertions.assertBackgroundThread()
    val originFile = originFile
    val simpleName = EditorTabPresentationUtil.getEditorTabTitle(project, originFile)
    return if (contentManager.findContent(simpleName) != null) {
        EditorTabPresentationUtil.getUniqueEditorTabTitle(project, originFile)
    } else {
        simpleName
    }
}

fun VirtualFile?.toKotlinNotebookBackedFile(): BackedNotebookVirtualFile? {
    return if (this == null || !isKotlinNotebook) {
        null
    } else {
        toBackedNotebookFile()
    }
}
