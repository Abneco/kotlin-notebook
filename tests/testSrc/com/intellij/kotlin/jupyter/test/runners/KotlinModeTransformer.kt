// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.runners

import com.intellij.kotlin.jupyter.test.currentKotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.junit.runner.Description
import org.junit.runners.model.FrameworkMember

/**
 * A JUnit test or class annotated with this will only run if the Kotlin Compiler is running in K1 mode.
 */
annotation class K1Only(val reason: String = "")

/**
 * A JUnit test or class annotated with this will only run if the Kotlin Compiler is running in K2 mode.
 */
annotation class K2Only(val reason: String = "")

object KotlinModeTransformer : TestTransformer {
    override fun transformTest(testData: TestData): List<TestData> {
        if (testData.method.isIgnoredByPluginMode()) {
            return emptyList()
        }

        val newName = "${testData.description.methodName} (${currentKotlinPluginMode.name} Kotlin)"
        val newDescription = Description.createTestDescription(testData.method.declaringClass, newName)

        val newTestData = TestData(
            newDescription,
            testData.method,
            testData.testBody,
        )

        return listOf(newTestData)
    }

    internal fun FrameworkMember<*>?.isIgnoredByPluginMode(): Boolean {
        val member = this

        return when (currentKotlinPluginMode) {
            KotlinPluginMode.K1 -> member?.getMethodOrClassAnnotation<K2Only>() != null
            KotlinPluginMode.K2 -> member?.getMethodOrClassAnnotation<K1Only>() != null
        }
    }
}
