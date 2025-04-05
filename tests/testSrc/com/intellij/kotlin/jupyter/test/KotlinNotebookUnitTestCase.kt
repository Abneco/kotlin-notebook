// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.intellij.kotlin.jupyter.test.runners.KotlinNotebookTestRunner
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.runner.RunWith

@RunWith(KotlinNotebookTestRunner::class)
abstract class KotlinNotebookUnitTestCase : BasePlatformTestCase()
