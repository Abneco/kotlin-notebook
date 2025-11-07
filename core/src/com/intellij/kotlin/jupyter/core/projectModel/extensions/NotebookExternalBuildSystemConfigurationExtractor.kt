// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectModel.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk


data class ExternalBuildSystemSetting(
    val jdkName: String,
    val sdk: Sdk?,
    val moduleNames: Set<String>
)

/**
 * Provides general information in the form of [ExternalBuildSystemSetting] about an external build system.
 * e.g., gradle, maven, etc.
 *
 * Each EP targets a particular build system to keep runtime dependencies separated by modules.
 */
interface NotebookExternalBuildSystemConfigurationExtractor {
    /**
     * Returns [ExternalBuildSystemSetting], if [projectSettings] are recognized by the current EP.
     */
    fun getRecognizedBuildSettings(project: Project, projectSettings: ExternalProjectSettings): ExternalBuildSystemSetting?

    companion object {
        private val EP: ExtensionPointName<NotebookExternalBuildSystemConfigurationExtractor> = ExtensionPointName.create("com.intellij.kotlin.jupyter.core.externalBuildSystemSettingExtractor")

        fun getFromProviders(project: Project, settings: ExternalProjectSettings): Sequence<ExternalBuildSystemSetting> {
            val extensions = EP.extensionList
            if (extensions.isEmpty()) return emptySequence()

            return sequence {
                for (externalSystem in extensions) {
                    val buildSettings = externalSystem.getRecognizedBuildSettings(project, settings)
                    if (buildSettings == null) continue

                    yield(buildSettings)
                }
            }
        }
    }
}