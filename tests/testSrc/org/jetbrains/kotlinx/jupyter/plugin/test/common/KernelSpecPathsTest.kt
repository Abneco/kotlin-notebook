// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.test.common

import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.spec.KernelSpecDetector
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class KernelSpecPathsTest : UsefulTestCase() {

    @Test
    fun `test possible kernel dirs should be non-empty`() {
        val dirs = KernelSpecDetector.getPossibleDirs()
        Assert.assertTrue(dirs.size >= 2)
    }

    @Test
    fun `test kernels should be resolved if installed`() {
        val kernelsDir = KernelSpecDetector.getKernelsDir()
        if (kernelsDir == null) {
            LOG.warn("Kernels dir is empty")
            return
        } else {
            LOG.warn("Kernels dir: ${kernelsDir.absolutePath}")
        }
    }
}
