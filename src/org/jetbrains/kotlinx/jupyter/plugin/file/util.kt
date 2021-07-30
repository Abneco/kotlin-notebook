// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.file

import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.plugins.notebooks.core.impl.file.NotebookVirtualFile

val NotebookVirtualFile.isKotlinNotebook: Boolean get() {
    return notebook.language == KotlinLanguage.INSTANCE
}
