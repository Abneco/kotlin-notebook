// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process

import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinKernelName

class KotlinNotebookSessionLaunchStrategy : JupyterSessionVerifiedLaunchStrategy(3) {
    override suspend fun isApplicable(kernelName: String): Boolean {
        return isKotlinKernelName(kernelName)
    }
}
