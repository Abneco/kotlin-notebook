// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.attached

import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelEvent

class AttachedKernelEvent(
    override val source: AttachedKernelProcessHandler
) : KotlinKernelEvent