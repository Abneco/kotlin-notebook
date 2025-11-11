// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.runners

import org.junit.runner.Description
import org.junit.runners.model.FrameworkMethod

data class TestData(
    val description: Description,
    val method: FrameworkMethod,
    val context: TestContext,
)

interface TestTransformer {
    fun transformTest(testData: TestData): List<TestData>
}
