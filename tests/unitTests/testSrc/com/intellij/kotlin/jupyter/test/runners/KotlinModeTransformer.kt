// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.runners

import com.intellij.kotlin.jupyter.test.currentKotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.junit.runner.Description
import org.junit.runners.model.FrameworkMember

/**
 * A JUnit test or class annotated with this will only run if the Kotlin Compiler is running in K1 mode.
 */
annotation class K1Only(@Suppress("unused") val reason: String = "")

/**
 * A JUnit test or class annotated with this will only run if the Kotlin Compiler is running in K2 mode.
 */
annotation class K2Only(@Suppress("unused") val reason: String = "")

object KotlinModeTransformer : TestTransformer {
    override fun transformTest(testData: TestData): List<TestData> {
        if (testData.method.isIgnoredByPluginMode()) {
            return emptyList()
        }

        val newDescription = if (isOnTeamCity) {
            val newName = "${testData.description.methodName} (${currentKotlinPluginMode.name} Kotlin)"
            Description.createTestDescription(testData.method.declaringClass, newName)
        } else {
            testData.description
        }

        return listOf(testData.copy(description = newDescription))
    }

    internal fun FrameworkMember<*>?.isIgnoredByPluginMode(): Boolean {
        val member = this

        return when (currentKotlinPluginMode) {
            KotlinPluginMode.K1 -> member?.getMethodOrClassAnnotation<K2Only>() != null
            KotlinPluginMode.K2 -> member?.getMethodOrClassAnnotation<K1Only>() != null
        }
    }

    private val isOnTeamCity get() = System.getenv("TEAMCITY_VERSION") != null
}
