// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.language.meta.psi

import com.intellij.kotlin.jupyter.core.language.meta.JupyterKtMetaLanguage
import com.intellij.psi.tree.IElementType

class JKTMetaElementType(debugName: String) : IElementType(debugName, JupyterKtMetaLanguage)
