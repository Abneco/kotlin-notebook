// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.intellij.kotlin.jupyter.test.runners.KotlinNotebookTestRunner
import com.intellij.kotlin.jupyter.test.runners.ListenableTest
import com.intellij.kotlin.jupyter.test.runners.ListenableTestImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.runner.RunWith

@RunWith(KotlinNotebookTestRunner::class)
abstract class KotlinNotebookUnitTestCase :
    BasePlatformTestCase(),
    ListenableTest by ListenableTestImpl()
{
    override fun setUp() {
        wrapSetUp(this) { super.setUp() }
    }

    override fun tearDown() {
        wrapTearDown(this) { super.tearDown() }
    }
}
