// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k1.scriptingSupport

import com.intellij.kotlin.jupyter.core.scriptingSupport.CompiledClassifiersDefaultImportsEnhancer
import com.intellij.kotlin.jupyter.core.scriptingSupport.TwoPartsList
import com.intellij.openapi.Disposable
import org.jetbrains.kotlinx.jupyter.repl.result.SerializedCompiledScript


class CompiledClassifiersDefaultImportsEnhancerFactoryK1: CompiledClassifiersDefaultImportsEnhancer.Factory {
    override fun create(parentDisposable: Disposable): CompiledClassifiersDefaultImportsEnhancer {
        return object : CompiledClassifiersDefaultImportsEnhancer {
            // do nothing
            override fun updateDefaultImports(
                compiledClassifiers: List<SerializedCompiledScript>,
                additionalDefaultImports: TwoPartsList<String>
            ) = Unit

            override fun clear() = Unit
        }
    }
}