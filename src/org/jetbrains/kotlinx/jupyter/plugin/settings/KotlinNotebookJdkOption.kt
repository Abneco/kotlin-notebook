// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed interface KotlinNotebookJdkOption {
    fun getPath(project: Project): String?
}

class JdkOptionWithPath(val homePath: String): KotlinNotebookJdkOption {
    override fun getPath(project: Project): String {
        return homePath
    }
}

@OptIn(ExperimentalContracts::class)
private fun isSuitableForStartingKernel(sdk: Sdk?): Boolean {
    contract { returns(true) implies (sdk != null) }
    if (sdk == null) return false
    return sdk.sdkType is JavaSdk && JavaSdk.getInstance().getVersion(sdk)?.isAtLeast(JavaSdkVersion.JDK_11) == true
}

object ProjectJdkOption : KotlinNotebookJdkOption {
    override fun getPath(project: Project): String? {
        val rootManager = ProjectRootManager.getInstance(project)
        val projectSdk = rootManager.projectSdk
        if (isSuitableForStartingKernel(projectSdk)) return projectSdk.homePath

        val suitableJdk = ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance()).firstOrNull {
            isSuitableForStartingKernel(it)
        } ?: return null
        return suitableJdk.homePath
    }
}