// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings

import com.intellij.kotlin.jupyter.core.settings.ui.MavenVersionComboBox
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion

object KotlinKernelVersions {
    val DEBUG_SUPPORTED = KotlinKernelVersion.fromMavenVersion("0.12.0-137")!!
}

val KotlinKernelVersion.isKernelVersionEnoughForInstrumentation: Boolean
    get() = compareTo(KotlinKernelVersions.DEBUG_SUPPORTED) >= 0

val Project.isKernelVersionEnoughForInstrumentation: Boolean
    get() = (selectedKernelVersion?.isKernelVersionEnoughForInstrumentation ?: false)

val MavenVersionComboBox.selectedKernelVersion: KotlinKernelVersion?
    get() = KotlinKernelVersion.fromMavenVersion(version)