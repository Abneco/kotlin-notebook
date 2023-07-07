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

    companion object {
        fun fromName(name: String?): KotlinNotebookJdkOption {
            if (name == null) return ProjectJdkOption
            return NamedJdkOption(name)
        }
    }
}

class NamedJdkOption(private val name: String): KotlinNotebookJdkOption {
    override fun getPath(project: Project): String? {
        return ProjectJdkTable.getInstance().findJdk(name, JavaSdk.getInstance().name)?.homePath
    }
}

private val runtimeJavaSdkVersion: JavaSdkVersion? by lazy {
    val runtimeVersion = Runtime.version()
    JavaSdkVersion.fromVersionString(runtimeVersion.toString())
}

internal val minJdkVersion get() = JavaSdkVersion.JDK_11
internal val maxJdkVersion get() = runtimeJavaSdkVersion

@OptIn(ExperimentalContracts::class)
internal fun isSuitableForStartingKernel(sdk: Sdk?): Boolean {
    contract { returns(true) implies (sdk != null) }
    if (sdk == null) return false
    if (sdk.sdkType !is JavaSdk) return false
    val version = JavaSdk.getInstance().getVersion(sdk) ?: return false
    return minJdkVersion <= version && (maxJdkVersion == null || version <= maxJdkVersion)
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