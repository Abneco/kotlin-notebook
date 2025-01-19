// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.nbformat.JupyterChangeListener
import com.intellij.jupyter.core.jupyter.nbformat.JupyterNotebook
import com.intellij.jupyter.core.jupyter.nbformat.NotebookChanged
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.notebooks.jupyter.core.jupyter.JupyterFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
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
import kotlin.reflect.KMutableProperty0

@Service(Service.Level.PROJECT)
class KotlinNotebookPerFileSettingsCache(val project: Project, private val coroutineScope: CoroutineScope) : Disposable {
    private val cache = CollectionFactory.createConcurrentWeakMap<VirtualFile, KotlinNotebookSettings>()

    fun notebookEditorCreated(file: VirtualFile) {
        if (cache.contains(file)) return

        val notebookFile = BackedNotebookVirtualFile.takeIfBacked(file) ?: return
        val jupyterChangeListener = JupyterChangeListener { event ->
            when (event) {
                is NotebookChanged -> refreshSettings(notebookFile)
            }
        }
        notebookFile.notebook.listeners.changeListeners.addListener(jupyterChangeListener, this)

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
            notebookLogger().error("Cached settings unavailable for $notebookFile")
            if (ApplicationManager.getApplication().isDispatchThread) {
                return refreshSettings(notebookFile)
            }
            return KotlinNotebookSettings.DEFAULT
        }
        return cachedSettings
    }

    @CalledInAny
    fun getCachedSettings(file: VirtualFile): KotlinNotebookSettings? = cache[file]

    internal fun onModulesRenamed(oldToNewNames: Map<String, String>) = performRefactoring {
        onModulesRenamed(this::notebookDependencies, oldToNewNames)
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
                    FilenameIndex.getAllFilesByExt(project, JupyterFileType.defaultExtension, ProjectScope.getProjectScope(project))
                }
                val closedNotebookFiles = ipynbFiles
                    .filter { !openFiles.contains(it) }
                    .mapNotNull { BackedNotebookVirtualFile.Companion.takeBackend(it) }
                    .filter { it.file.isKotlinNotebook }
                if (closedNotebookFiles.isNotEmpty()) {
                    withContext(Dispatchers.EDT) {
                        writeIntentReadAction {
                            closedNotebookFiles.forEach { it.notebook.operation() }
                            FileDocumentManager.getInstance().saveAllDocuments()
                        }
                    }
                }
            }
        }
    }

    private fun onModulesRenamed(property: KMutableProperty0<KotlinNotebookDependencies>, oldToNewNames: Map<String, String>) {
        val oldModule = property.get() as? KotlinNotebookDependencies.SingleModule ?: return
        property.set(KotlinNotebookDependencies.SingleModule(oldToNewNames[oldModule.moduleName] ?: oldModule.moduleName))
    }

    override fun dispose() {
        cache.clear()
    }

    companion object {
        fun getInstance(project: Project): KotlinNotebookPerFileSettingsCache = project.service<KotlinNotebookPerFileSettingsCache>()
    }
}

class KotlinNotebookModuleRenameListener : ModuleListener {
    override fun modulesRenamed(project: Project, modules: List<Module>, oldNameProvider: Function<in Module, String>) {
        val oldToNewModuleNames = modules.associateBy { oldNameProvider.`fun`(it) }.mapValues { it.value.name }
        KotlinNotebookPerFileSettingsCache.getInstance(project).onModulesRenamed(oldToNewModuleNames)
    }
}
