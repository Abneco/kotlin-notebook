// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.outputs.error

import com.intellij.jupyter.core.jupyter.editor.outputs.error.JupyterErrorOutputCustomizer
import com.intellij.jupyter.core.jupyter.nbformat.JupyterNotebook
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import org.jetbrains.annotations.Nls

class KotlinNotebookErrorOutputCustomizer : JupyterErrorOutputCustomizer {
    override fun getFoldedTextPlaceholder(notebook: JupyterNotebook?): @Nls String? {
        if (notebook?.isKotlinNotebook == true) {
            return KotlinNotebookBundle.message("notebook.error.output.traceback.kotlin.ellipsis")
        }
        return null
    }
}
