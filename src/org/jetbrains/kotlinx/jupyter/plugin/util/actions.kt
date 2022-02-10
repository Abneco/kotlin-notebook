// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.vfs.VirtualFile

internal fun DataContext.getVirtualFile(): VirtualFile? = CommonDataKeys.VIRTUAL_FILE.getData(this)
internal fun AnActionEvent.getVirtualFile(): VirtualFile? = dataContext.getVirtualFile()