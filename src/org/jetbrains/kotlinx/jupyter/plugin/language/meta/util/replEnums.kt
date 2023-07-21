package org.jetbrains.kotlinx.jupyter.plugin.language.meta.util

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
