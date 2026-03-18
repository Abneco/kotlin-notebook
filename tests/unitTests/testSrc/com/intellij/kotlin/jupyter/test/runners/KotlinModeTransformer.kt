// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.runners

import com.intellij.kotlin.jupyter.test.currentKotlinPluginMode
import org.junit.runner.Description

object KotlinModeTransformer : TestTransformer {
    override fun transformTest(testData: TestData): List<TestData> {
        val newDescription = if (isOnTeamCity) {
            val newName = "${testData.description.methodName} (${currentKotlinPluginMode.name} Kotlin)"
            Description.createTestDescription(testData.method.declaringClass, newName)
        } else {
            testData.description
        }

        return listOf(testData.copy(description = newDescription))
    }

    private val isOnTeamCity get() = System.getenv("TEAMCITY_VERSION") != null
}
