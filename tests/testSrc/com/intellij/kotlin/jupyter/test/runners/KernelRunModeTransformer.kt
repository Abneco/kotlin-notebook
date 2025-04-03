// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.runners

import com.intellij.kotlin.jupyter.core.settings.DEFAULT
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import org.junit.runner.Description

object TestContext {
    private val modeThreadLocal = object : ThreadLocal<KotlinNotebookSessionRunMode>() {
        override fun initialValue() = KotlinNotebookSessionRunMode.DEFAULT
    }

    var kernelRunMode: KotlinNotebookSessionRunMode
        get() = modeThreadLocal.get()
        set(value) = modeThreadLocal.set(value)
}

annotation class RunModeAwareTest

object KernelRunModeTransformer : TestTransformer {
    override fun transformTest(testData: TestData): List<TestData> {
        val annotation = testData.method.getMethodOrClassAnnotation<RunModeAwareTest>()
        if (annotation == null) return listOf(testData)

        return listOf(
            KotlinNotebookSessionRunMode.SEPARATE_PROCESS,
            KotlinNotebookSessionRunMode.IDE_PROCESS,
        ).map { mode ->
            val testName = "${testData.description.methodName} ($mode)"
            val newDescription = Description.createTestDescription(testData.method.declaringClass, testName)

            TestData(
                newDescription,
                testData.method
            ) { method, description, notifier ->
                try {
                    TestContext.kernelRunMode = mode
                    testData.testBody.runTest(method, description, notifier)
                } finally {
                    TestContext.kernelRunMode = KotlinNotebookSessionRunMode.DEFAULT
                }
            }
        }
    }
}
