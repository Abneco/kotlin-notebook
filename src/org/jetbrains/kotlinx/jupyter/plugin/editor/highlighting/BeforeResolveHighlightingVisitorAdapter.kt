// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import org.jetbrains.kotlin.idea.highlighter.BeforeResolveHighlightingVisitor

class BeforeResolveHighlightingVisitorAdapter: AbstractKotlinHighlightingVisitorAdapter<BeforeResolveHighlightingVisitor>(
    { annotationHolder -> BeforeResolveHighlightingVisitor(annotationHolder) }
) {
    override fun clone(): HighlightVisitor {
        return BeforeResolveHighlightingVisitorAdapter()
    }
}
