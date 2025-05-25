// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.codeInsight.metaLanguage

import com.intellij.lang.Commenter
import com.intellij.lang.LanguageCommenters
import org.jetbrains.kotlin.idea.KotlinLanguage

class JKTMetaCommenter : Commenter {
    private val kotlinCommenter: Commenter? get() = LanguageCommenters.INSTANCE.forLanguage(KotlinLanguage.INSTANCE)

    override fun getLineCommentPrefix(): String? = kotlinCommenter?.lineCommentPrefix
    override fun getBlockCommentPrefix(): String? = kotlinCommenter?.blockCommentPrefix
    override fun getBlockCommentSuffix(): String? = kotlinCommenter?.blockCommentSuffix
    override fun getCommentedBlockCommentPrefix(): String? = kotlinCommenter?.commentedBlockCommentPrefix
    override fun getCommentedBlockCommentSuffix(): String? = kotlinCommenter?.commentedBlockCommentSuffix
}
