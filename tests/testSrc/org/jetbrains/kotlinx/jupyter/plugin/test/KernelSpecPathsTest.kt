// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.test

import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlinx.jupyter.plugin.KernelSpecDetector
import org.junit.Assert

class KernelSpecPathsTest : UsefulTestCase() {
    fun `test possible kernel dirs should be non-empty`() {
        val dirs = KernelSpecDetector.getPossibleDirs()
        Assert.assertTrue(dirs.size >= 2)
    }

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
