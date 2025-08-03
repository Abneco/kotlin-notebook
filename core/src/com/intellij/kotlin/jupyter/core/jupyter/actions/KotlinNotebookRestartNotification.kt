// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.actions

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.Key

object KotlinNotebookRestartNotification {
    fun getStatus(fileEditor: FileEditor): KotlinNotebookRestartStatus = fileEditor.getUserData(statusKey) ?: KotlinNotebookRestartStatus.NotNeeded
    fun setStatus(fileEditor: FileEditor, status: KotlinNotebookRestartStatus): Unit = fileEditor.putUserData(statusKey, status)

    private val statusKey: Key<KotlinNotebookRestartStatus> = Key.create("JupyterFileEditorDependenciesStatus")
}
