// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.codeInsight.metaLanguage

import org.jetbrains.kotlinx.jupyter.common.ReplCommand
import org.jetbrains.kotlinx.jupyter.common.ReplEnum
import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.plugin.language.meta.psi.JKTMetaCommandStatement
import org.jetbrains.kotlinx.jupyter.plugin.language.meta.psi.JKTMetaMagicStatement
import org.jetbrains.kotlinx.jupyter.plugin.language.meta.psi.JKTMetaStatement

val JKTMetaStatement?.replEnum: ReplEnum<*>?
    get() {
        return when (this) {
            is JKTMetaMagicStatement -> ReplLineMagic
            is JKTMetaCommandStatement -> ReplCommand
            else -> null
        }
    }
