// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport

import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.storage.entities
import org.jetbrains.kotlinx.jupyter.plugin.ide.handlers.KotlinPluginModeAwareHandler
import org.jetbrains.kotlinx.jupyter.plugin.ide.handlers.createPluginModeAwareInstance

/**
 * A functional interface that acts as a checker to determine if a script is present in Workspace Model,
 * depending on the mode of the Kotlin plugin (K1 or K2).
 *
 * This is crucial to now since only after a script was added to the Model, it's now in indexes.
 */
fun interface PluginModeAwareScriptPresenceChecker : KotlinPluginModeAwareHandler {
    fun checkPresentInCache(lastCompiledScriptPath: String): Boolean

    companion object {
        fun create(project: Project): PluginModeAwareScriptPresenceChecker {
            return createPluginModeAwareInstance(
                project,
                ::createForK1,
                ::createForK2
            )
        }

        private fun createForK1(project: Project): PluginModeAwareScriptPresenceChecker {
            return PluginModeAwareScriptPresenceChecker { lastCompiledScriptPath: String ->
                val cache = project.scriptConfigurationsClassCache

                cache.allDependenciesClassFiles.any {
                    it.presentableUrl == lastCompiledScriptPath
                }
            }
        }

        private fun createForK2(project: Project) : PluginModeAwareScriptPresenceChecker {
            return PluginModeAwareScriptPresenceChecker { lastCompiledScriptPath: String ->
                val cache = project.workSpaceSnapshot

                cache.entities<LibraryEntity>()
                    .filter {
                        it.roots.any { root -> root.url.url.contains(lastCompiledScriptPath) }
                    }.iterator().hasNext()
            }
        }
    }
}