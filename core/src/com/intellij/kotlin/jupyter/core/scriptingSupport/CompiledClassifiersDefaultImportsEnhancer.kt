// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.kotlin.jupyter.core.ide.handlers.KotlinPluginModeAwareHandler
import com.intellij.kotlin.jupyter.core.scriptingSupport.CompiledClassifiersDefaultImportsEnhancer.Companion.create
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.repl.result.SerializedCompiledScript

/**
 * Keeps track of the classifiers that have been obtained from compiled scripts.
 *
 * Its main method aims to amend, if needed, additional imports configuration.
 *
 * [create] calls a [Factory] service for each of K1/K2 modes.
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
        fun create(project: Project, parentDisposable: Disposable): CompiledClassifiersDefaultImportsEnhancer {
            return project.service<Factory>().create(parentDisposable)
        }
    }
}