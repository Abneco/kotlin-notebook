// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.buildSystems.gradle

import com.intellij.kotlin.jupyter.core.projectModel.ExternalBuildSystemSetting
import com.intellij.kotlin.jupyter.core.projectModel.NotebookExternalBuildSystemConfigurationExtractor
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings

class GradleConfigurationExtractor : NotebookExternalBuildSystemConfigurationExtractor {
    override fun getRecognizedBuildSettings(project: Project, projectSettings: ExternalProjectSettings): ExternalBuildSystemSetting? {
        val jdkTable = ProjectJdkTable.getInstance()
        if (projectSettings !is GradleProjectSettings) {
            return null
        }

        val jdkName = projectSettings.gradleJvm ?: return null
        val sdk = jdkTable.findJdk(jdkName) ?: return null

        return ExternalBuildSystemSetting(
          jdkName,
          sdk,
          projectSettings.modules.toSet()
        )
    }
}