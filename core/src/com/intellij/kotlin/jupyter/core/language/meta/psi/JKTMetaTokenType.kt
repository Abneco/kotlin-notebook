// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.language.meta.psi

import com.intellij.notebooks.jupyter.core.jupyter.JupyterLanguage
import com.intellij.psi.tree.IElementType

class JKTMetaTokenType(debugName: String) : IElementType(debugName, JupyterLanguage) {
    override fun toString() = "JKTMetaTokenType.${super.toString()}"
}
