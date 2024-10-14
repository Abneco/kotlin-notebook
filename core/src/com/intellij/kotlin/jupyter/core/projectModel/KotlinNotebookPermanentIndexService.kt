// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectModel

import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.io.File

@Service(Service.Level.PROJECT)
class KotlinNotebookPermanentIndexService(val project: Project) {
  fun addToPermanentIndex(classpath: List<String>, sourceClasspath: List<String>) {
    KotlinNotebookPluginScope.getForProject(project).async(Dispatchers.EDT) {
        writeAction {
            addToPermanentIndexImpl(classpath, sourceClasspath)
        }
    }
  }

  private fun getPermanentScriptingLibrary(): Library {
    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
    val library = libraryTable.getLibraryByName(SCRIPT_DEPENDENCIES_LIBRARY_NAME)
    if (library != null) return library
    return libraryTable.createLibrary(SCRIPT_DEPENDENCIES_LIBRARY_NAME)
  }

  private fun addToPermanentIndexImpl(classpath: List<String>, sourceClasspath: List<String>) {
    val newLibrary = getPermanentScriptingLibrary()

    val model = newLibrary.modifiableModel
    val existingRoots = buildMap<OrderRootType, Set<String>> {
      for (rootType in listOf(OrderRootType.CLASSES, OrderRootType.SOURCES)) {
        put(rootType, newLibrary.rootProvider.getUrls(rootType).toSet())
      }
    }

    fun addPath(path: String, rootType: OrderRootType) {
      if (path.endsWith(".jar")) {
        val rootPath = "file://${File(path).invariantSeparatorsPath}"
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

  companion object {
    internal const val SCRIPT_DEPENDENCIES_LIBRARY_NAME = "Permanent Script Dependencies"

    fun getInstance(project: Project) = project.service<KotlinNotebookPermanentIndexService>()
  }
}
