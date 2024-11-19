// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k1.highlighting

import com.intellij.kotlin.jupyter.core.editor.highlighting.visitors.KaDiagnosticData
import com.intellij.kotlin.jupyter.core.editor.highlighting.visitors.KotlinPluginModeShadowingAnalyzerHandler
import com.intellij.kotlin.jupyter.core.editor.highlighting.visitors.toKaSeverity
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.highlighter.AbstractKotlinHighlightVisitor.Companion.suppressHighlight
import org.jetbrains.kotlin.idea.highlighter.AbstractKotlinHighlightVisitor.Companion.unsuppressHighlight
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

class ShadowingVisitorFactoryK1 : KotlinPluginModeShadowingAnalyzerHandler.Factory {
    override fun create(): KotlinPluginModeShadowingAnalyzerHandler {
        return K1ShadowingAnalyzerHandler
    }
}

object K1ShadowingAnalyzerHandler : KotlinPluginModeShadowingAnalyzerHandler() {
    // This is required to use the default Kt visitor for K1
    override fun applyBeforeProcessingDiagnostic(diagnostic: KaDiagnosticData) {
        val psiElement = diagnostic.psiElement

        if (diagnostic.severity == KaSeverity.ERROR && psiElement is KtElement) {
            psiElement.suppressHighlight()
        }
    }

    // This is required to use the default Kt visitor for K1
    override fun beforeLeavingAnalysisSession(ktFile: KtFile) {
        ktFile.unsuppressHighlight()
    }

    //todo:  Use of K1-specific API as there is no way to invoke with AnalysisMode == ALL_COMPILER_CHECKS from analysis api
    override fun KaSession.collectDiagnostics(file: KtFile) : Collection<KaDiagnosticData> {
        return file.analyzeWithAllCompilerChecks().bindingContext.diagnostics.all().map {
            KaDiagnosticData(
                it.psiElement,
                it.severity.toKaSeverity(),
                it.factory.name
            )
        }
    }
}