// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.core.impl.file.notebook
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterChangeListener
import org.jetbrains.plugins.notebooks.jupyter.nbformat.NotebookChanged

@Service(Service.Level.PROJECT)
class KotlinNotebookPerFileSettingsCache(val project: Project) : Disposable {
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
        val cachedSettings = cache[notebookFile.file]
        if (cachedSettings == null) {
            thisLogger().error("Cached settings unavailable for $notebookFile")
            if (ApplicationManager.getApplication().isDispatchThread) {
                return refreshSettings(notebookFile)
            }
            return KotlinNotebookSettings.DEFAULT
        }
        return cachedSettings
    }

    override fun dispose() {
        cache.clear()
    }

    companion object {
        fun getInstance(project: Project) = project.service<KotlinNotebookPerFileSettingsCache>()
    }
}