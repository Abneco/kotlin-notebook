// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.liveTemplates

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import org.jetbrains.kotlinx.jupyter.plugin.i18n.JupyterKotlinBundle
import org.jetbrains.kotlinx.jupyter.plugin.language.meta.psi.JKTMetaPSIFile

// We can show this context type to users, but it might be confusing
object KotlinJupyterMetaTemplateContextType : TemplateContextType(JupyterKotlinBundle.message("kotlin.jupyter.template.context.type.meta")) {
    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        return templateActionContext.file is JKTMetaPSIFile
    }
}
