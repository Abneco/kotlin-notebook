package org.jetbrains.kotlinx.jupyter.plugin.lang.grammar

import com.intellij.lexer.FlexAdapter
import org.jetbrains.kotlinx.jupyter.plugin.psi.meta._JKTMetaLexer

class JKTMetaLexerAdapter : FlexAdapter(_JKTMetaLexer())
