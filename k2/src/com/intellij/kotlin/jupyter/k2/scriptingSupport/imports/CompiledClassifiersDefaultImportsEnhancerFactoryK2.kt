// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport.imports

import com.intellij.kotlin.jupyter.core.scriptingSupport.CompiledClassifiersDefaultImportsEnhancer
import com.intellij.kotlin.jupyter.core.scriptingSupport.TwoPartsList
import com.intellij.kotlin.jupyter.core.scriptingSupport.classFQN
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlinx.jupyter.repl.result.SerializedCompiledScript


class CompiledClassifiersDefaultImportsEnhancerFactoryK2 : CompiledClassifiersDefaultImportsEnhancer.Factory {
    override fun create(parentDisposable: Disposable): CompiledClassifiersDefaultImportsEnhancer {
        return CompiledClassifiersDefaultImportsEnhancerK2(parentDisposable)
    }
}

/**
 * This class is a temporary solution for K1-backed REPL in K2 IDEA mode to allow implicit import
 * of classifiers while preserving K1 behavior.
 */
internal class CompiledClassifiersDefaultImportsEnhancerK2(
    parentDisposable: Disposable
) : CompiledClassifiersDefaultImportsEnhancer, Disposable {
    init {
        Disposer.register(parentDisposable, this)
    }
    private val compiledScriptClassifiers = mutableSetOf<String>()

    private fun List<SerializedCompiledScript>.getTopLevelClassifiersFQN(
        redeclaredClasses: MutableCollection<String>
    ): List<String> {
        return mapNotNull { classifier ->
            val classFQN = classifier.classFQN
            // Get classifiers only on the top level
            val simpleName = classFQN.substringAfter('.')
            if (simpleName.contains('.')) {
                return@mapNotNull null
            }

            if (!compiledScriptClassifiers.add(simpleName)) {
                redeclaredClasses.add(simpleName)
            }
            classFQN
        }
    }

    override fun updateDefaultImports(
        compiledClassifiers: List<SerializedCompiledScript>,
        additionalDefaultImports: TwoPartsList<String>
    ) {
        val redeclaredClassifiers = mutableListOf<String>()
        val imports = compiledClassifiers.getTopLevelClassifiersFQN(redeclaredClassifiers)

        // Remove redeclared ones from previous imports
        val overlappedImports = additionalDefaultImports.getList().filter { import ->
            redeclaredClassifiers.any { s -> import.contains(s) }
        }

        additionalDefaultImports.removeSnippet(overlappedImports)
        additionalDefaultImports.addSnippet(imports)
    }

    override fun clear() {
        compiledScriptClassifiers.clear()
    }

    override fun dispose() {
        clear()
    }
}