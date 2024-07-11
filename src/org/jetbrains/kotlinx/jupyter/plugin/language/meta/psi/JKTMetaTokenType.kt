package org.jetbrains.kotlinx.jupyter.plugin.language.meta.psi

import com.intellij.psi.tree.IElementType
import com.intellij.notebooks.jupyter.core.jupyter.JupyterLanguage

class JKTMetaTokenType(debugName: String) : IElementType(debugName, JupyterLanguage) {
    override fun toString() = "JKTMetaTokenType.${super.toString()}"
}
