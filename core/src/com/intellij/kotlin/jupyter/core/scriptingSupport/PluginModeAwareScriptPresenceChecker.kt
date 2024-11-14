// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.ide.handlers.KotlinPluginModeAwareHandler
import com.intellij.kotlin.jupyter.core.ide.handlers.createPluginModeAwareInstance
import com.intellij.kotlin.jupyter.core.scriptingSupport.k2.NotebookScriptConfigurationsSource
import com.intellij.kotlin.jupyter.core.scriptingSupport.k2.getConfigurationsForNotebook
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.storage.entities
import org.jetbrains.kotlin.idea.core.script.scriptConfigurationsSourceOfType


private data class ScriptCheckerConfiguration(
    val project: Project,
    val notebookFile: BackedNotebookVirtualFile
)

/**
 * A functional interface that acts as a checker to determine if a script is present in Workspace Model,
 * depending on the mode of the Kotlin plugin (K1 or K2).
 *
 * This is crucial to now since only after a script was added to the Model, it's now in indexes.
 */
fun interface PluginModeAwareScriptPresenceChecker : KotlinPluginModeAwareHandler {
    fun checkPresentInCache(lastCompiledScriptPath: String): Boolean

    companion object {
        fun create(project: Project, virtualFile: BackedNotebookVirtualFile): PluginModeAwareScriptPresenceChecker {
            return createPluginModeAwareInstance(
                ScriptCheckerConfiguration(
                    project,
                    virtualFile
                ),
                Companion::createForK1,
                Companion::createForK2
            )
        }

        private fun createForK1(checkerConfiguration: ScriptCheckerConfiguration): PluginModeAwareScriptPresenceChecker {
            val project = checkerConfiguration.project

            return PluginModeAwareScriptPresenceChecker { lastCompiledScriptPath: String ->
                val cache = project.scriptConfigurationsClassCache

                cache.allDependenciesClassFiles.any {
                    it.presentableUrl == lastCompiledScriptPath
                }
            }
        }

        private fun createForK2(checkerConfiguration: ScriptCheckerConfiguration) : PluginModeAwareScriptPresenceChecker {
            val (project, notebookFile) = checkerConfiguration

            return PluginModeAwareScriptPresenceChecker { lastCompiledScriptPath: String ->
                fun checkSourceIsNotEmpty(): Boolean {
                    val scriptConfigurationsSource = project.scriptConfigurationsSourceOfType<NotebookScriptConfigurationsSource>()?.data?.get() ?: return false
                    return scriptConfigurationsSource.getConfigurationsForNotebook(notebookFile.file)?.isNotEmpty() == true
                }

                val cache = project.workSpaceSnapshot

                checkSourceIsNotEmpty() && cache.entities<LibraryEntity>()
                    .filter {
                        it.roots.any { root -> root.url.url.contains(lastCompiledScriptPath) }
                    }.iterator().hasNext()
            }
        }
    }
}