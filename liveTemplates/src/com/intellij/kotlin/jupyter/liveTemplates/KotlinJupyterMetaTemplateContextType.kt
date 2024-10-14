// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.liveTemplates

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.kotlin.jupyter.core.language.meta.psi.JKTMetaPSIFile
import com.intellij.kotlin.jupyter.liveTemplates.i18n.KotlinNotebookLiveTemplatesBundle

// We can show this context type to users, but it might be confusing
object KotlinJupyterMetaTemplateContextType : TemplateContextType(KotlinNotebookLiveTemplatesBundle.message("kotlin.jupyter.template.context.type.meta")) {
    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        return templateActionContext.file is JKTMetaPSIFile
    }
}
