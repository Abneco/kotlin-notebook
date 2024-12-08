// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.runners

import com.intellij.kotlin.jupyter.test.currentKotlinPluginMode
import com.intellij.kotlin.jupyter.test.runners.KotlinPluginAwareRunner.Companion.isIgnoredByPluginMode
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.junit.runner.Runner
import org.junit.runner.notification.RunNotifier
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMember
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.TestClass
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters
import org.junit.runners.parameterized.ParametersRunnerFactory
import org.junit.runners.parameterized.TestWithParameters

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
    companion object {
        internal fun FrameworkMember<*>?.isIgnoredByPluginMode(testClass: TestClass): Boolean {
            val member = this

            return when (currentKotlinPluginMode) {
                KotlinPluginMode.K1 -> member?.getMethodOrClassAnnotation<K2Only>(testClass) != null
                KotlinPluginMode.K2 -> member?.getMethodOrClassAnnotation<K1Only>(testClass) != null
            }
        }

        private inline fun <reified T: Annotation> FrameworkMember<*>.getMethodOrClassAnnotation(testClass: TestClass): T? {
            val annotation = testClass.getAnnotation(T::class.java)
            return annotation ?: getAnnotation(T::class.java)
        }
    }

    override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
        if (isIgnored(method)) {
            val description = describeChild(method)
            notifier.fireTestIgnored(description)
            return
        }

        super.runChild(method, notifier)
    }

    override fun isIgnored(child: FrameworkMethod?): Boolean {
        // consider Junit
        if (super.isIgnored(child)) {
            return true
        }
        return child.isIgnoredByPluginMode(testClass)
    }
}

/**
 * This factory is used to customize behavior of [org.junit.runners.Parameterized] test suite to
 * respect [KotlinPluginMode] annotations.
 * If a method or class is having different plugin mode, it will be ignored.
 */
class PluginModeAwareParametersRunnerFactory : ParametersRunnerFactory {
    override fun createRunnerForTestWithParameters(test: TestWithParameters): Runner {
        return object : BlockJUnit4ClassRunnerWithParameters(test) {
            override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
                if (isIgnored(method)) {
                    notifier.fireTestIgnored(
                        describeChild(method)
                    )
                    return
                }
                super.runChild(method, notifier)
            }

            override fun isIgnored(method: FrameworkMethod?): Boolean {
                if (super.isIgnored(method)) {
                    return true
                }
                return method.isIgnoredByPluginMode(testClass)
            }
        }
    }
}