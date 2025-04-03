// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings

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

abstract class AbstractKotlinNotebookJdkOption : KotlinNotebookJdkOption {
    abstract fun getSdk(project: Project): Sdk?

    override fun getPath(project: Project): String? {
        return getSdk(project)?.homePath
    }

    override fun getVersion(project: Project): JavaSdkVersion? {
        return getSdk(project)?.jdkVersion()
    }
}

class NamedJdkOption(private val name: String) : AbstractKotlinNotebookJdkOption() {
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
    private val javaHomeEnvironmentVariablesToTry = listOf(
        "KOTLIN_JUPYTER_JAVA_HOME",
        "JRE_HOME",
        "JDK_HOME",
        "JDK_11",
        "JAVA_HOME",
    )

    override fun getSdk(project: Project): Sdk? {
        return getJdksToTry(project)
            .firstOrNull { isSuitableForStartingKernel(it.sdk) }
            ?.also { sdkToTry ->
                if (sdkToTry.shouldCache) {
                    getCache(project).cachedLastResortJdk = sdkToTry.sdk
                }
            }
            ?.sdk
    }

    private class SdkToTry(
        val sdk: Sdk,
        val shouldCache: Boolean,
    )

    private suspend fun SequenceScope<SdkToTry>.yieldSdk(sdk: Sdk?, shouldCache: Boolean) {
        if (sdk == null) return
        yield(SdkToTry(sdk, shouldCache))
    }

    private fun getJdksToTry(project: Project): Sequence<SdkToTry> {
        return sequence {
            val rootManager = ProjectRootManager.getInstance(project)
            yieldSdk(rootManager.projectSdk, shouldCache = false)

            val jdkType = JavaSdk.getInstance()
            for (sdk in ProjectJdkTable.getInstance().getSdksOfType(jdkType)) {
                yieldSdk(sdk, shouldCache = false)
            }
            yieldSdk(getCache(project).cachedLastResortJdk, shouldCache = false)

            for (path in getJavaHomePathsFromEnvironment()) {
                val jdk = jdkType.createJdk(
                    "Kotlin Notebook JDK for project ${project.name}",
                    path,
                )
                yieldSdk(jdk, shouldCache = true)
            }
        }
    }

    private fun getCache(project: Project) =
        KotlinNotebookLastResortJdkCache.getInstance(project)

    private fun getJavaHomePathsFromEnvironment(): Sequence<String> {
        return sequence {
            for (variableName in javaHomeEnvironmentVariablesToTry) {
                val variableValue = System.getenv(variableName) ?: continue
                if (variableValue.isBlank()) continue
                yield(variableValue)
            }
            val currentJavaHome = System.getProperty("java.home")
            if (currentJavaHome != null && currentJavaHome.isNotBlank()) {
                yield(currentJavaHome)
            }
        }
    }
}
