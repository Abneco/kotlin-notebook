// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.k2

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlinx.jupyter.plugin.ide.handlers.KotlinPluginModeAwareHandler
import org.jetbrains.kotlinx.jupyter.plugin.ide.handlers.createPluginModeAwareInstance
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.TwoPartsList
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.classFQN
import org.jetbrains.kotlinx.jupyter.repl.result.SerializedCompiledScript


/**
 * Keeps track of the classifiers that have been obtained from compiled scripts.
 *
 * Its main method aims to amend, if needed, additional imports configuration.
 */
internal interface CompiledClassifiersDefaultImportsEnhancer : KotlinPluginModeAwareHandler {
    fun updateDefaultImports(
        compiledClassifiers: List<SerializedCompiledScript>,
        additionalDefaultImports: TwoPartsList<String>
    )

    fun clear()

    companion object {
        private val TRACKER_K1 = object : CompiledClassifiersDefaultImportsEnhancer {
            // do nothing

            override fun updateDefaultImports(
                compiledClassifiers: List<SerializedCompiledScript>,
                additionalDefaultImports: TwoPartsList<String>
            ) = Unit

            override fun clear() = Unit
        }

        fun create(parentDisposable: Disposable) = createPluginModeAwareInstance(
            parentDisposable,
            { TRACKER_K1 },
            ::CompiledClassifiersDefaultImportsEnhancerK2
        )
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