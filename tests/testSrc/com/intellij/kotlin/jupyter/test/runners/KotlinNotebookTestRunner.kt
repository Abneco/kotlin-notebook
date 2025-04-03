// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.runners

import org.junit.runner.notification.RunNotifier
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod

/**
 * This is the universal runner for Kotlin Notebook tests.
 * It combines the results of application of multiple [TestTransformer]s
 * and runs resulting test bodies, accompanied by test descriptions.
 *
 * Instead of creating new runner, better create new [TestTransformer] and add it to the list
 */
class KotlinNotebookTestRunner(klass: Class<*>) : BlockJUnit4ClassRunner(klass) {
    private val myTransformers = listOf(
        KotlinModeTransformer,
        KernelRunModeTransformer,
    )

    public override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
        val defaultDescription = describeChild(method)
        if (isIgnored(method)) {
            notifier.fireTestIgnored(defaultDescription)
            return
        }

        val initialTestData = TestData(
            defaultDescription,
            method
        ) { method, description, notifier ->
            runLeaf(methodBlock(method), description, notifier)
        }

        val transformedTests = myTransformers.fold(listOf(initialTestData)) { acc, transformer ->
            buildList {
                for (test in acc) {
                    addAll(transformer.transformTest(test))
                }
            }
        }

        for (test in transformedTests) {
            test.testBody.runTest(
                test.method,
                test.description,
                notifier,
            )
        }
    }
}
