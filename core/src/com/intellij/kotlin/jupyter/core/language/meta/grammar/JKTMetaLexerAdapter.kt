// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.language.meta.grammar

import com.intellij.kotlin.jupyter.core.psi.meta._JKTMetaLexer
import com.intellij.lexer.FlexAdapter

class JKTMetaLexerAdapter : FlexAdapter(_JKTMetaLexer())
