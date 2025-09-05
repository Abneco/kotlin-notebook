// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectModel

import com.intellij.jupyter.core.jupyter.nbformat.JupyterNotebook
import com.intellij.kotlin.jupyter.core.notifications.KotlinNotebookNotifications
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookDependencies
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookProjectOptionsProvider
import com.intellij.kotlin.jupyter.core.settings.jdkVersion
import com.intellij.kotlin.jupyter.core.settings.notebookDependencies
import com.intellij.kotlin.jupyter.core.util.getSourceRoots
import com.intellij.kotlin.jupyter.core.util.rootBasePath
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import kotlin.io.path.invariantSeparatorsPathString

internal data class ExternalBuildSystemSetting(
    val jdkName: String,
    val sdk: Sdk?,
    val moduleNames: Set<String>
)

/**
 * Traverses all registered [ExternalSystemManager] and returns a sequence of [ExternalBuildSystemSetting] matching current project.
 * The main purpose is to retrieve gradle JDK and module names to suggest a proper JDK for the kernel startup.
 * Right now, only [GradleProjectSettings] are specifying JDK.
 */
internal fun Project.getExternalBuildSystemModulesInfo(): Sequence<ExternalBuildSystemSetting> {
    val jdkTable = ProjectJdkTable.getInstance()
    val project = this

    return sequence {
        for (manager in ExternalSystemManager.EP_NAME.extensionList) {
            val externalSystemSettings = manager.settingsProvider.`fun`(this@getExternalBuildSystemModulesInfo) ?: continue
            val linkedProjectSettingsCollection = externalSystemSettings.linkedProjectsSettings
            for (projectSettings in linkedProjectSettingsCollection) {
                val projectPath = projectSettings.externalProjectPath
                if (projectPath.isNullOrEmpty()) continue
                if (projectPath != project.rootBasePath) continue

                val jdkName = when (projectSettings) {
                    is GradleProjectSettings -> projectSettings.gradleJvm ?: continue
                    else -> null
                }

                if (jdkName == null) continue
                val sdk = jdkTable.findJdk(jdkName) ?: continue

                yield(
                    ExternalBuildSystemSetting(
                        jdkName,
                        sdk,
                        projectSettings.modules.toSet()
                    )
                )
            }
        }
    }
}

fun JupyterNotebook.showKernelAndModuleJdkAreMatchingWarningIfNeeded(project: Project) {
    val sdkAlignmentCheck = checkKernelAndModuleSdksAreMatching(project, notebookDependencies)
    when (sdkAlignmentCheck) {
        is KernelJdkAlignmentCheckResult.JdksAligned, KernelJdkAlignmentCheckResult.NotebookJdkIsNotSet -> return
        is KernelJdkAlignmentCheckResult.MisalignedWithModule -> {}
    }

    KotlinNotebookNotifications.getInstance(project).showKernelAndProjectModuleJdkAreNotAlignedWarning(
        sdkAlignmentCheck
    )
}

/**
 * Checks that current kernel JDK and JDK, which is used to build project module, are aligned.
 * Note that check is performed only if [KotlinNotebookDependencies.SingleModule] is set.
 */
internal fun checkKernelAndModuleSdksAreMatching(
    project: Project,
    selectedDependencies: KotlinNotebookDependencies,
): KernelJdkAlignmentCheckResult {
    if (selectedDependencies !is KotlinNotebookDependencies.SingleModule) return KernelJdkAlignmentCheckResult.JdksAligned
    val currentSdk = KotlinNotebookProjectOptionsProvider.getInstance(project).jdk.getVersion(project)
    val externalBuildSystemsInfo = project.getExternalBuildSystemModulesInfo()
    if (!externalBuildSystemsInfo.iterator().hasNext()) {
        return KernelJdkAlignmentCheckResult.JdksAligned
    }
    // we did not set up any
    if (currentSdk == null) return KernelJdkAlignmentCheckResult.NotebookJdkIsNotSet

    val sourceRoots = selectedDependencies.getSourceRoots(project).map {
        it.invariantSeparatorsPathString
    }

    for (buildSettings in externalBuildSystemsInfo) {
        val matchedModule = buildSettings.moduleNames.firstOrNull {
            sourceRoots.any { root -> root.contains(it) }
        }
        if (matchedModule == null) continue

        if (buildSettings.jdkName == currentSdk.name) {
            break
        }
        val sdk = buildSettings.sdk
        val sdkWithVersion = sdk?.jdkVersion() ?: continue

        if (currentSdk.maxLanguageLevel < sdkWithVersion.maxLanguageLevel) {
            return KernelJdkAlignmentCheckResult.MisalignedWithModule(
                sdk,
                conflictingModule = selectedDependencies.moduleName
            )
        }
    }

    return KernelJdkAlignmentCheckResult.JdksAligned
}

/**
 * Hierarchy representing different JDK-related checks.
 *
 * @see checkKernelAndModuleSdksAreMatching
 */
internal sealed interface KernelJdkAlignmentCheckResult {
    object JdksAligned : KernelJdkAlignmentCheckResult
    object NotebookJdkIsNotSet: KernelJdkAlignmentCheckResult
    data class MisalignedWithModule(
        val suggestedSdk: Sdk?,
        val conflictingModule: String
    ) : KernelJdkAlignmentCheckResult
}