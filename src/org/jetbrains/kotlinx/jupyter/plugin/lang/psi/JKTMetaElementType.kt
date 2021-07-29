package org.jetbrains.kotlinx.jupyter.plugin.lang.psi

import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlinx.jupyter.plugin.lang.JupyterKtMetaLanguage

class JKTMetaElementType(debugName: String) : IElementType(debugName, JupyterKtMetaLanguage)
