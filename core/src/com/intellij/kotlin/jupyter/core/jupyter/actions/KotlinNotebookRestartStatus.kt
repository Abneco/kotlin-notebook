// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.actions

import org.jetbrains.annotations.Nls

sealed class KotlinNotebookRestartStatus {
    object NotNeeded : KotlinNotebookRestartStatus()
    class Needed(@Nls val message: String) : KotlinNotebookRestartStatus()
}
