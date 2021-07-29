package org.jetbrains.kotlinx.jupyter.plugin

import org.junit.Assert
import org.junit.Test

class KernelSpecPathsTests : BaseTest() {
    @Test
    fun `possible kernel dirs should be non-empty`() {
        val dirs = KernelSpecDetector.getPossibleDirs()
        Assert.assertTrue(dirs.size >= 2)
    }

    @Test
    fun `kernels should be resolved if installed`() {
        val kernelsDir = KernelSpecDetector.getKernelsDir()
        if (kernelsDir == null) {
            log.warn("Kernels dir is empty")
            return
        } else {
            log.warn("Kernels dir: ${kernelsDir.absolutePath}")
        }
    }
}
