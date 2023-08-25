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

    fun getVersion(project: Project): JavaSdkVersion?

    companion object {
        fun fromName(name: String?): KotlinNotebookJdkOption {
            if (name == null) return ProjectJdkOption
            return NamedJdkOption(name)
        }
    }
}

abstract class AbstractKotlinNotebookJdkOption: KotlinNotebookJdkOption {
    abstract fun getSdk(project: Project): Sdk?

    override fun getPath(project: Project): String? {
        return getSdk(project)?.homePath
    }

    override fun getVersion(project: Project): JavaSdkVersion? {
        return getSdk(project)?.jdkVersion()
    }
}

class NamedJdkOption(private val name: String): AbstractKotlinNotebookJdkOption() {
    private val mySdk by lazy {
        ProjectJdkTable.getInstance().findJdk(name, JavaSdk.getInstance().name)
    }

    override fun getSdk(project: Project): Sdk? = mySdk
}

internal val minJdkVersion get() = JavaSdkVersion.JDK_11

@OptIn(ExperimentalContracts::class)
internal fun isSuitableForStartingKernel(sdk: Sdk?): Boolean {
    contract { returns(true) implies (sdk != null) }
    if (sdk == null) return false
    val version = sdk.jdkVersion() ?: return false
    return minJdkVersion <= version
}

internal fun Sdk.jdkVersion(): JavaSdkVersion? {
    if (sdkType !is JavaSdk) return null
    return JavaSdk.getInstance().getVersion(this)
}

object ProjectJdkOption : AbstractKotlinNotebookJdkOption() {
    override fun getSdk(project: Project): Sdk? {
        val rootManager = ProjectRootManager.getInstance(project)
        val projectSdk = rootManager.projectSdk
        if (isSuitableForStartingKernel(projectSdk)) return projectSdk

        val suitableJdk = ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance()).firstOrNull {
            isSuitableForStartingKernel(it)
        } ?: return null
        return suitableJdk
    }
}
