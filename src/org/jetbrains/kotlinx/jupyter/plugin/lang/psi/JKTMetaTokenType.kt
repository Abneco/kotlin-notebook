package org.jetbrains.kotlinx.jupyter.plugin.lang.psi

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.notebooks.jupyter.JupyterLanguage

class JKTMetaTokenType(debugName: String) : IElementType(debugName, JupyterLanguage) {
    override fun toString() = "JKTMetaTokenType.${super.toString()}"
}
