// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectModel

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString

/**
 * This service is used to store the classpath of the notebooks used in this project
 * so that for the second time indexing won't take that much time.
 */
@Service(Service.Level.PROJECT)
class KotlinNotebookPermanentIndexService(
    val project: Project,
    val coroutineScope: CoroutineScope,
) {
    private val projectLibraryTable get() = LibraryTablesRegistrar.getInstance().getLibraryTable(project)

    fun addToPermanentIndex(classpath: List<String>, sourceClasspath: List<String>) {
        coroutineScope.async(Dispatchers.EDT) {
            edtWriteAction {
                addToPermanentIndexImpl(classpath, sourceClasspath)
            }
        }
    }

    fun removeFromPermanentIndex(artifactsPaths: Collection<String>) {
        val library = getPermanentScriptingLibrary() ?: return
        val model = library.modifiableModel
        val existingRoots = getLibraryRoots(library)

        for (rootType in listOf(OrderRootType.CLASSES, OrderRootType.SOURCES)) {
            for (rootPath in existingRoots[rootType]!!) {
                if (artifactsPaths.any { rootPath.contains(it) }) {
                    model.removeRoot(rootPath, rootType)
                }
            }
        }
        model.commit()
    }

    val currentClassRoots: Collection<VirtualFile> get() {
        val library = getPermanentScriptingLibrary() ?: return emptySet()

        return library.rootProvider.getFiles(OrderRootType.CLASSES).toSet()
    }

    private fun getLibraryRoots(library: Library): Map<OrderRootType, Set<String>> {
        return buildMap {
            for (rootType in listOf(OrderRootType.CLASSES, OrderRootType.SOURCES)) {
                put(rootType, library.rootProvider.getUrls(rootType).toSet())
            }
        }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun addToPermanentIndexImpl(classpath: List<String>, sourceClasspath: List<String>) {
        val newLibrary = getOrCreatePermanentScriptingLibrary()

        val model = newLibrary.modifiableModel
        val existingRoots = getLibraryRoots(newLibrary)

        fun addPath(path: String, rootType: OrderRootType) {
            if (path.endsWith(".jar")) {
                val rootPath = "file://${Path(path).invariantSeparatorsPathString}"
                if (existingRoots[rootType]!!.contains(rootPath)) return
                model.addRoot(rootPath, rootType)
            }
        }

        for (path in classpath) {
            addPath(path, OrderRootType.CLASSES)
        }
        for (path in sourceClasspath) {
            addPath(path, OrderRootType.SOURCES)
        }
        model.commit()
    }

    private fun getPermanentScriptingLibrary(): Library? {
        return projectLibraryTable.getLibraryByName(SCRIPT_DEPENDENCIES_LIBRARY_NAME)
    }

    private fun getOrCreatePermanentScriptingLibrary(): Library {
        return getPermanentScriptingLibrary() ?:
            projectLibraryTable.createLibrary(SCRIPT_DEPENDENCIES_LIBRARY_NAME)
    }

    companion object {
        internal const val SCRIPT_DEPENDENCIES_LIBRARY_NAME = "Permanent Script Dependencies"

        fun getInstance(project: Project): KotlinNotebookPermanentIndexService = project.service<KotlinNotebookPermanentIndexService>()
    }
}
