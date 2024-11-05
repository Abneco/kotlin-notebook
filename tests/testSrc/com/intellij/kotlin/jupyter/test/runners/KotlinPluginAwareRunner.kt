// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.runners

import com.intellij.kotlin.jupyter.test.currentKotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.junit.runner.Description
import org.junit.runner.notification.RunNotifier
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod

/**
 * A JUnit test or class annotated with this will only run if the Kotlin Compiler is running in K1 mode.
 */
annotation class K1Only(val reason: String = "")

/**
 * A JUnit test or class annotated with this will only run if the Kotlin Compiler is running in K2 mode.
 */
annotation class K2Only(val reason: String = "")

/**
 * Custom runner that makes it possible to annotate test methods to either run on K1, K2 or both.
 *
 * When using this class tests will be run depending on two annotations: [K1Only] and [K2Only].
 *
 * The semantics when using these on a test are:
 *
 * - `@K1Only`: The test will only run if the Kotlin compiler is running in K1 mode.
 * - `@K2Only`: The test will only run if the Kotlin compiler is running in K2 mode.
 * - No annotation: The test will run in both K1 and K2 mode.
 *
 * If conflicting annotations are present on both the class and a test method, the
 * annotation on the class take precedence.
 *
 * Notebook tests are run in K1 or K2 mode depending on which IntelliJ run configuration is used.
 * Currently, they are named See "Kotlin Notebook Tests K1" and "Kotlin Notebook Tests K2"
 */
class KotlinPluginAwareRunner(klass: Class<*>) : BlockJUnit4ClassRunner(klass) {
    override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
        val defaultDescription = getTestDescription(method)
        if (!isIgnored(method)) {
            runLeaf(methodBlock(method), defaultDescription, notifier)
            return
        }

        val reason = getIgnoredReason(method)
        val description = if (reason != null) {
            Description.createTestDescription(
                defaultDescription.testClass,
                "${description.displayName} [Ignored: $reason]",
            )
        } else {
            defaultDescription
        }

        notifier.fireTestIgnored(description)
    }

    //override fun describeChild(method: FrameworkMethod): Description = getTestDescription(method)

    override fun isIgnored(child: FrameworkMethod?): Boolean {
        return when (currentKotlinPluginMode) {
            KotlinPluginMode.K1 -> child?.getMethodOrClassAnnotation<K2Only>() != null
            KotlinPluginMode.K2 -> child?.getMethodOrClassAnnotation<K1Only>() != null
        }
    }

    private fun getIgnoredReason(child: FrameworkMethod?) : String? {
        val ignoredReason = when (currentKotlinPluginMode) {
            KotlinPluginMode.K1 -> child?.getMethodOrClassAnnotation<K2Only>()?.reason
            KotlinPluginMode.K2 -> child?.getMethodOrClassAnnotation<K1Only>()?.reason
        }

        return if (ignoredReason == null || ignoredReason.isEmpty() == true) {
            null
        } else {
            ignoredReason
        }
    }

    private fun getTestDescription(method: FrameworkMethod): Description {
        val mode = getModeDescription(method)
        val testName = "${method.name} <$mode>"
        return Description.createTestDescription(method.method.declaringClass, testName)
    }

    // If test only runs on a single mode, we always use the name of that mode in the test name
    // so it is clear from UI why a certain test is being skipped. If a test does run, we use
    // the name of the current mode.
    private fun getModeDescription(method: FrameworkMethod): String {
        // Any class annotations take precedence over method annotation.
        if (method.getMethodOrClassAnnotation<K1Only>() != null) {
            return KotlinPluginMode.K1.name
        }
        if (method.getMethodOrClassAnnotation<K2Only>() != null) {
            return KotlinPluginMode.K2.name
        }

        return currentKotlinPluginMode.name
    }

    private inline fun <reified T: Annotation> FrameworkMethod.getMethodOrClassAnnotation(): T? {
        val annotation = testClass.getAnnotation(T::class.java)

        return annotation ?: getAnnotation(T::class.java)
    }
}
