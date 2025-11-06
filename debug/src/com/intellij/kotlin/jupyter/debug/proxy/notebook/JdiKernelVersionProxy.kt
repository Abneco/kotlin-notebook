// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.notebook

import com.intellij.kotlin.jupyter.debug.proxy.JdiProxyApiDelegate

/**
 * Artificial interface for JDI proxy of [org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion] object.
 */
interface JdiKernelVersionProxy : JdiProxyApiDelegate {
    fun toMavenVersion(): String

    fun toPyPiVersion(): String
}