// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.codeInsight.metaLanguage

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.kotlin.jupyter.core.language.meta.psi.JKTMetaCommandStatement
import com.intellij.kotlin.jupyter.core.language.meta.psi.JKTMetaMagicStatement
import com.intellij.kotlin.jupyter.core.language.meta.psi.JKTMetaStatement
import org.jetbrains.kotlinx.jupyter.common.ReplCommand
import org.jetbrains.kotlinx.jupyter.common.ReplEnum
import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic

val JKTMetaStatement?.replEnum: ReplEnum<*>?
    get() {
        return when (this) {
            is JKTMetaMagicStatement -> ReplLineMagic
            is JKTMetaCommandStatement -> ReplCommand
            else -> null
        }
    }

internal fun ReplEnum<*>.toLookupElements(): List<LookupElement> {
    return this.codeInsightValues.map {
        LookupElementBuilder.create(it.name).withTypeText(it.type.name)
    }
}
