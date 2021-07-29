package org.jetbrains.kotlinx.jupyter.plugin.lang.util

import org.jetbrains.kotlinx.jupyter.common.ReplCommand
import org.jetbrains.kotlinx.jupyter.common.ReplEnum
import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.plugin.lang.psi.JKTMetaCommandStatement
import org.jetbrains.kotlinx.jupyter.plugin.lang.psi.JKTMetaMagicStatement
import org.jetbrains.kotlinx.jupyter.plugin.lang.psi.JKTMetaStatement

val JKTMetaStatement?.replEnum: ReplEnum<*>?
    get() {
        return when (this) {
            is JKTMetaMagicStatement -> ReplLineMagic
            is JKTMetaCommandStatement -> ReplCommand
            else -> null
        }
    }
