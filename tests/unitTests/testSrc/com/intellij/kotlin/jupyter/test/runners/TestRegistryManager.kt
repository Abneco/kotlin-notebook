// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.runners

import com.intellij.openapi.util.registry.Registry

object TestRegistryManager : TestListener {
    private val registryPairs = listOf(
        "llm.enable.mock.response" to true,
    )

    override fun beforeSetUp(testInstance: Any) {
        for ((key, value) in registryPairs) {
            Registry.get(key).setValue(value)
        }
    }
}