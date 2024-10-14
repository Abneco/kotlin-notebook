// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.runners

import com.intellij.kotlin.jupyter.core.settings.DEFAULT
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import org.junit.runner.Description
import org.junit.runner.notification.RunNotifier
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod

object TestContext {
    private val modeThreadLocal = object : ThreadLocal<KotlinNotebookSessionRunMode>() {
        override fun initialValue() = KotlinNotebookSessionRunMode.DEFAULT
    }

    var kernelRunMode: KotlinNotebookSessionRunMode
        get() = modeThreadLocal.get()
        set(value) = modeThreadLocal.set(value)
}

class RunModeAwareTestRunner(klass: Class<*>) : BlockJUnit4ClassRunner(klass) {
    override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
        val defaultDescription = describeChild(method)
        notifier.fireTestIgnored(defaultDescription)
        if (isIgnored(method)) {
            return
        }

        listOf(
            KotlinNotebookSessionRunMode.SEPARATE_PROCESS,
            KotlinNotebookSessionRunMode.IDE_PROCESS,
        ).forEach { mode ->
            runTestMethodWithMode(method, notifier, mode)
        }
    }

    private fun runTestMethodWithMode(method: FrameworkMethod, notifier: RunNotifier, mode: KotlinNotebookSessionRunMode) {
        try {
            TestContext.kernelRunMode = mode
            val description = getTestDescription(method)
            runLeaf(methodBlock(method), description, notifier)
        } finally {
            TestContext.kernelRunMode = KotlinNotebookSessionRunMode.DEFAULT
        }
    }

    private fun getTestDescription(method: FrameworkMethod): Description {
        val testName = "${method.name} (${TestContext.kernelRunMode})"
        return Description.createTestDescription(method.method.declaringClass, testName)
    }
}
