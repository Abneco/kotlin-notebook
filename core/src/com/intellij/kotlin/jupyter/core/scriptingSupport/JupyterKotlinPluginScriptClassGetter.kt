// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import org.jetbrains.kotlinx.jupyter.compiler.PreviousScriptClassesProvider
import kotlin.reflect.KClass
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.GetScriptingClassByClassLoader
import kotlin.script.experimental.jvm.JvmGetScriptingClass

class JupyterKotlinPluginScriptClassGetter(
    private val baseClass: KClass<*>,
    private val previousScriptClassesProvider: PreviousScriptClassesProvider,
) : GetScriptingClassByClassLoader {
    private val getScriptingClass = JvmGetScriptingClass()

    private val lastClassLoader: ClassLoader?
        get() = previousScriptClassesProvider.get().lastOrNull()?.run { fromClass?.java?.classLoader } ?: baseClass.java.classLoader

    override fun invoke(
        classType: KotlinType,
        contextClass: KClass<*>,
        hostConfiguration: ScriptingHostConfiguration
    ): KClass<*> {
        return getScriptingClass(classType, lastClassLoader ?: contextClass.java.classLoader, hostConfiguration)
    }

    override fun invoke(
        classType: KotlinType,
        contextClassLoader: ClassLoader?,
        hostConfiguration: ScriptingHostConfiguration
    ): KClass<*> {
        return getScriptingClass(classType, lastClassLoader ?: contextClassLoader, hostConfiguration)
    }
}
