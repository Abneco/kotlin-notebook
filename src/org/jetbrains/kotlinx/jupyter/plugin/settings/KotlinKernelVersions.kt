// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.debugFeaturesEnabled
import org.jetbrains.kotlinx.jupyter.plugin.settings.ui.MavenVersionComboBox

object KotlinKernelVersions {
    val DEBUG_SUPPORTED = KotlinKernelVersion.fromMavenVersion("0.12.0-137")!!
}

val KotlinKernelVersion.isKernelVersionEnoughForInstrumentation: Boolean
    get() = compareTo(KotlinKernelVersions.DEBUG_SUPPORTED) >= 0

val Project.isKernelVersionEnoughForInstrumentation: Boolean
    get() = (selectedKernelVersion?.isKernelVersionEnoughForInstrumentation ?: false)
            && debugFeaturesEnabled

val MavenVersionComboBox.selectedKernelVersion: KotlinKernelVersion?
    get() = KotlinKernelVersion.fromMavenVersion(version)