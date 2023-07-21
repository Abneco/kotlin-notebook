package org.jetbrains.kotlinx.jupyter.plugin.language.meta.psi

import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlinx.jupyter.plugin.language.meta.JupyterKtMetaLanguage

class JKTMetaElementType(debugName: String) : IElementType(debugName, JupyterKtMetaLanguage)
