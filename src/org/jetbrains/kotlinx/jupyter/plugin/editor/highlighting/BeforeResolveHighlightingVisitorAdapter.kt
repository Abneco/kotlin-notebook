// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import org.jetbrains.kotlin.idea.highlighter.BeforeResolveHighlightingVisitor

class BeforeResolveHighlightingVisitorAdapter: AbstractKotlinHighlightingVisitorAdapter<BeforeResolveHighlightingVisitor>() {
    override fun clone(): HighlightVisitor {
        return BeforeResolveHighlightingVisitorAdapter()
    }

    override fun createVisitor(holder: HighlightInfoHolder): BeforeResolveHighlightingVisitor {
        return BeforeResolveHighlightingVisitor(holder)
    }
}
