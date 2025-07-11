// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process

import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KERNEL_VERIFICATION_ATTEMPTS_COUNT
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KERNEL_VERIFICATION_RECONNECT_ATTEMPTS_COUNT
import com.intellij.kotlin.jupyter.core.util.isKotlinKernelName

class KotlinNotebookSessionLaunchStrategy : JupyterSessionVerifiedLaunchStrategy(
    verificationAttemptsCount = KERNEL_VERIFICATION_ATTEMPTS_COUNT,
    reconnectAttemptsCount = KERNEL_VERIFICATION_RECONNECT_ATTEMPTS_COUNT,
) {
    override suspend fun isApplicable(kernelName: String): Boolean {
        return isKotlinKernelName(kernelName)
    }
}
