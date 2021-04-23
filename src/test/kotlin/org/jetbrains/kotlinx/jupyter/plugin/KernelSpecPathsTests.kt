package org.jetbrains.kotlinx.jupyter.plugin

import io.kotlintest.matchers.collections.shouldHaveAtLeastSize
import org.junit.jupiter.api.Test

class KernelSpecPathsTests : BaseTest() {
    @Test
    fun `possible kernel dirs should be non-empty`() {
        val dirs = KernelSpecDetector.getPossibleDirs()
        dirs shouldHaveAtLeastSize 2
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
