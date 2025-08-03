// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectModel

import com.intellij.openapi.vfs.VirtualFile

@JvmInline
value class NotebookId(val virtualFile: VirtualFile)