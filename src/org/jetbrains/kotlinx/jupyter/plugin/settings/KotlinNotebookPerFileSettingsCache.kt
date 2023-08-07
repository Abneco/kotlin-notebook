// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.serialization.impl.LibraryNameGenerator
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.util.Function
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.util.JUPYTER_NOTEBOOK_EXTENSION
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.core.impl.file.notebook
import org.jetbrains.plugins.notebooks.core.impl.file.originFile
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterChangeListener
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterNotebook
import org.jetbrains.plugins.notebooks.jupyter.nbformat.NotebookChanged
import kotlin.reflect.KMutableProperty0

@Service(Service.Level.PROJECT)
class KotlinNotebookPerFileSettingsCache(val project: Project, private val coroutineScope: CoroutineScope) : Disposable {
    private val cache = CollectionFactory.createConcurrentWeakMap<VirtualFile, KotlinNotebookSettings>()

    fun notebookEditorCreated(file: VirtualFile) {
        if (cache.contains(file)) return

        val notebookFile = BackedNotebookVirtualFile.takeIfBacked(file) ?: return
        val jupyterChangeListener = JupyterChangeListener { event ->
            if (event is NotebookChanged) {
                refreshSettings(notebookFile)
            }
        }
        notebookFile.notebook.addJupyterChangeListener(jupyterChangeListener)
        Disposer.register(this, Disposable { notebookFile.notebook.removeJupyterChangeListener(jupyterChangeListener) })

        notebookFile.notebook.migrateSettings()
        refreshSettings(notebookFile)
    }

    @RequiresEdt
    fun refreshSettings(notebookFile: BackedNotebookVirtualFile): KotlinNotebookSettings {
        val settings = notebookFile.notebook.readSettings()
        cache[notebookFile.file] = settings
        return settings
    }

    @CalledInAny
    fun getSettings(notebookFile: BackedNotebookVirtualFile): KotlinNotebookSettings {
        val cachedSettings = getCachedSettings(notebookFile.file)
        if (cachedSettings == null) {
            thisLogger().error("Cached settings unavailable for $notebookFile")
            if (ApplicationManager.getApplication().isDispatchThread) {
                return refreshSettings(notebookFile)
            }
            return KotlinNotebookSettings.DEFAULT
        }
        return cachedSettings
    }

    @CalledInAny
    fun getCachedSettings(file: VirtualFile) = cache[file]

    internal fun onLibrariesRenamed(oldToNewNames: Map<String, String>) = performRefactoring {
        onDependenciesRenamed(this::projectLibraries, oldToNewNames)
    }

    internal fun onModulesRenamed(oldToNewNames: Map<String, String>) = performRefactoring {
        onDependenciesRenamed(this::projectDependencies, oldToNewNames)
    }

    private fun performRefactoring(operation: JupyterNotebook.() -> Unit) {
        coroutineScope.async {
            withBackgroundProgress(project, KotlinNotebookBundle.message("kotlin.jupyter.settings.refactoring.progress")) {
                val openNotebookFiles = withContext(Dispatchers.EDT) {
                    val openNotebookFiles = cache.toMap().keys.mapNotNull { BackedNotebookVirtualFile.takeIfBacked(it) }
                    openNotebookFiles.forEach { it.notebook.operation() }
                    if (openNotebookFiles.isNotEmpty()) FileDocumentManager.getInstance().saveAllDocuments()
                    openNotebookFiles
                }
                val openFiles = openNotebookFiles.map { it.originFile }

                val ipynbFiles = smartReadAction(project) {
                    FilenameIndex.getAllFilesByExt(project, JUPYTER_NOTEBOOK_EXTENSION, ProjectScope.getProjectScope(project))
                }
                val closedNotebookFiles = ipynbFiles
                    .filter { !openFiles.contains(it) }
                    .mapNotNull { BackedNotebookVirtualFile.find(it) }
                    .filter { it.file.isKotlinNotebook }
                if (closedNotebookFiles.isNotEmpty()) {
                    withContext(Dispatchers.EDT) {
                        closedNotebookFiles.forEach { it.notebook.operation() }
                        FileDocumentManager.getInstance().saveAllDocuments()
                    }
                }
            }
        }
    }

    private fun onDependenciesRenamed(property: KMutableProperty0<KotlinNotebookDependencies>, oldToNewNames: Map<String, String>) {
        val oldDependencies = property.get()
        if (oldDependencies is KotlinNotebookDependencies.Selection) {
            val newDependencies = oldToNewNames.values.toMutableSet()
            oldToNewNames.forEach { (oldName, newName) ->
                if (newDependencies.remove(oldName)) {
                    newDependencies.add(newName)
                }
            }
            property.set(KotlinNotebookDependencies.Selection(newDependencies))
        }
    }

    override fun dispose() {
        cache.clear()
    }

    companion object {
        fun getInstance(project: Project) = project.service<KotlinNotebookPerFileSettingsCache>()
    }
}

class KotlinNotebookModuleRenameListener : ModuleListener {
    override fun modulesRenamed(project: Project, modules: List<Module>, oldNameProvider: Function<in Module, String>) {
        val oldToNewModuleNames = modules.associateBy { oldNameProvider.`fun`(it) }.mapValues { it.value.name }
        KotlinNotebookPerFileSettingsCache.getInstance(project).onModulesRenamed(oldToNewModuleNames)
    }
}

class KotlinNotebookLibraryRenameListener(val project: Project) : WorkspaceModelChangeListener {
    override fun changed(event: VersionedStorageChange) {
        val libraryRenames = event.getChanges(LibraryEntity::class.java).filterIsInstance<EntityChange.Replaced<LibraryEntity>>().filter {
            it.oldEntity.tableId is LibraryTableId.ProjectLibraryTableId
        }
        if (libraryRenames.isEmpty()) return

        val oldToNewLibraryNames = libraryRenames.mapNotNull { rename ->
            val idBefore = rename.oldEntity.symbolicId
            val idAfter = rename.newEntity.symbolicId
            if (idBefore == idAfter) return@mapNotNull null

            val oldName = LibraryNameGenerator.getLegacyLibraryName(idBefore) ?: return@mapNotNull null
            val newName = LibraryNameGenerator.getLegacyLibraryName(idAfter) ?: return@mapNotNull null

            oldName to newName
        }.toMap()
        if (oldToNewLibraryNames.isEmpty()) return

        KotlinNotebookPerFileSettingsCache.getInstance(project).onLibrariesRenamed(oldToNewLibraryNames)
    }
}