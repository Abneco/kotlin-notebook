// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.dataframe

import com.intellij.openapi.application.ApplicationInfo
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.extensions.KernelVmCommandCustomizer

class KotlinDataframeKernelVmCommandCustomizer : KernelVmCommandCustomizer {
    override fun addVmArguments(arguments: MutableList<String>) {
        val applicationInfo = ApplicationInfo.getInstance()
        val buildNumberString = "${applicationInfo.build.productCode};${applicationInfo.build.components.joinToString(separator = ";")}"
        arguments.apply {
            add("-DKTNB_IDE_BUILD_NUMBER=$buildNumberString")
        }
    }
}
