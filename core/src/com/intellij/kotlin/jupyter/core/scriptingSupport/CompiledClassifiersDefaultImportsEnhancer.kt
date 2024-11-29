// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.kotlin.jupyter.core.ide.handlers.KotlinPluginModeAwareHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlinx.jupyter.repl.result.SerializedCompiledScript

/**
 * Keeps track of the classifiers that have been obtained from compiled scripts.
 *
 * Its main method aims to amend, if needed, additional imports configuration.
 *
 * [Factory] is used to create handler from one of the modules.
 */
interface CompiledClassifiersDefaultImportsEnhancer : KotlinPluginModeAwareHandler {
    fun updateDefaultImports(
        compiledClassifiers: List<SerializedCompiledScript>,
        additionalDefaultImports: TwoPartsList<String>
    )

    fun clear()

    fun interface Factory {
        fun create(parentDisposable: Disposable): CompiledClassifiersDefaultImportsEnhancer
    }

    companion object {
        private val EP: ExtensionPointName<Factory> = ExtensionPointName.create("com.intellij.kotlin.jupyter.core.classifiersDefaultImportsEnhancerFactory")

        fun create(parentDisposable: Disposable): CompiledClassifiersDefaultImportsEnhancer {
            return EP.extensionList.first().create(parentDisposable)
        }
    }
}