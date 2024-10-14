// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process

import com.intellij.kotlin.jupyter.core.util.isKotlinKernelName

class KotlinNotebookSessionLaunchStrategy : JupyterSessionVerifiedLaunchStrategy(3) {
    override suspend fun isApplicable(kernelName: String): Boolean {
        return isKotlinKernelName(kernelName)
    }
}
