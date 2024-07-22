// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.liveTemplates

import com.intellij.codeInsight.template.EverywhereContextType
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.injected.editor.EditorWindow
import com.intellij.kotlin.jupyter.liveTemplates.i18n.KotlinNotebookLiveTemplatesBundle
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.liveTemplates.KotlinTemplateContextType
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook

sealed class KotlinNotebookTemplateContextType private constructor(
    private val templateContextTypeDelegate: TemplateContextType,
    @NlsContexts.Label @NonNls presentableName: String? = null,
) : TemplateContextType(presentableName ?: templateContextTypeDelegate.presentableName) {

    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        val editor = templateActionContext.editor
        if (editor !is EditorWindow || !editor.delegate.isKotlinNotebook) return false
        return templateContextTypeDelegate.isInContext(templateActionContext)
    }

    override fun createHighlighter(): SyntaxHighlighter? {
        return templateContextTypeDelegate.createHighlighter()
    }

    @Suppress("DialogTitleCapitalization")
    class Generic : KotlinNotebookTemplateContextType(EverywhereContextType(), KotlinNotebookLiveTemplatesBundle.message("kotlin.jupyter.template.context.type.generic"))
    class Class : KotlinNotebookTemplateContextType(KotlinTemplateContextType.Class())
    class Comment : KotlinNotebookTemplateContextType(KotlinTemplateContextType.Comment())
    class Expression : KotlinNotebookTemplateContextType(KotlinTemplateContextType.Expression())
    class Statement : KotlinNotebookTemplateContextType(KotlinTemplateContextType.Statement())
    class ObjectDeclaration : KotlinNotebookTemplateContextType(KotlinTemplateContextType.ObjectDeclaration())

    class Meta: KotlinNotebookTemplateContextType(KotlinJupyterMetaTemplateContextType)
}
