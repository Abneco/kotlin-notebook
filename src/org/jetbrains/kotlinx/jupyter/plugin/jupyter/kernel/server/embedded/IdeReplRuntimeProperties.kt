// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.config.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.repl.ReplRuntimeProperties

class IdeReplRuntimeProperties(
    override val version: KotlinKernelVersion?,
    override val jvmTargetForSnippets: String,
) : ReplRuntimeProperties by defaultRuntimeProperties {
    override val kotlinVersion: String = KotlinVersion.CURRENT.toString()
}
