// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.actions

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.Nls

object JupyterNotebookDependencies {
    fun getStatus(fileEditor: FileEditor): Status = fileEditor.getUserData(statusKey) ?: Status.UpToDate
    fun setStatus(fileEditor: FileEditor, status: Status): Unit = fileEditor.putUserData(statusKey, status)

    private val statusKey: Key<Status> = Key.create("JupyterFileEditorDependenciesStatus")

    sealed class Status {
        object UpToDate : Status()
        class NotUpToDate(@Nls val message: String) : Status()
    }
}