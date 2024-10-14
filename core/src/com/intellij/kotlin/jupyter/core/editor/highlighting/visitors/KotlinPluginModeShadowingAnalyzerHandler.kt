// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.visitors

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.util.InjectedFileHighlightingHelper
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.util.convertToShadowedDeclaration
import com.intellij.kotlin.jupyter.core.ide.handlers.KotlinPluginModeAwareHandler
import com.intellij.kotlin.jupyter.core.ide.handlers.createPluginModeAwareInstance
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.highlighter.clearAllKotlinUnresolvedReferenceKinds
import org.jetbrains.kotlin.psi.KtFile

/**
 * This is an intermediate class to represent diagnostics data
 * as there is no way to invoke analysis with full checks via
 * [KaDiagnosticProvider] directly.
 */
data class KaDiagnosticData(
    val psiElement: PsiElement,
    val severity: KaSeverity,
    val factoryName: String,
)

internal fun Severity.toKaSeverity(): KaSeverity {
    return when (this) {
        Severity.INFO -> KaSeverity.INFO
        Severity.ERROR -> KaSeverity.ERROR
        Severity.WARNING -> KaSeverity.WARNING
    }
}

/**
 * Base class for managing analysis outside the focus cell.
 * The handlers are aware of the plugin's operation mode (e.g., K1 or K2).
 *
 * This analysis involves detecting errors and creating a special highlighter for it.
 * All instances of this class should be stateless, ideally, objects.
 */
sealed class KotlinPluginModeShadowingAnalyzerHandler : KotlinPluginModeAwareHandler {
    abstract fun applyBeforeProcessingDiagnostic(diagnostic: KaDiagnosticData)

    protected fun filterDiagnostic(diagnostic: KaDiagnosticData): Boolean {
        return diagnostic.severity == KaSeverity.ERROR
    }

    protected open fun KaSession.collectDiagnostics(file: KtFile) : Collection<KaDiagnosticData> {
        return file.collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS).map { psi ->
            KaDiagnosticData(
                psi.psi,
                psi.severity,
                psi.factoryName
            )
        }
    }

    protected fun KaSession.collectErrorDiagnostics(file: KtFile) : List<KaDiagnosticData> {
        return collectDiagnostics(file)
            .onEach(::applyBeforeProcessingDiagnostic)
            .filter(::filterDiagnostic)
    }

    protected fun prepareForFile(injectedFile: PsiFile) : InjectedFileHighlightingHelper {
        val helper = InjectedFileHighlightingHelper(injectedFile)
        helper.markTargetHost()

        return helper
    }

    protected fun convertToShadowed(diagnostics: List<KaDiagnosticData>) : Collection<HighlightInfo> {
        return diagnostics.mapNotNull { diagnostic ->
            val info = convertToShadowedDeclaration(
                diagnostic.psiElement, diagnostic.factoryName
            )

            if (info == null) {
                reportFailedShadowing(diagnostic)
            }
            info
        }
    }

    protected fun reportFailedShadowing(diagnostic: Any) {
        LOG.warn("Cannot convert diagnostic to shadowed: $diagnostic")
    }

    open fun performShadowing(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, afterAnalysis: () -> Unit = {}): Boolean {
        if (file !is KtFile) return true

        val helper = prepareForFile(file)
        val isTargetHost = helper.isCurrentFileTarget

        if (isTargetHost) {
            afterAnalysis()
            return true
        }

        try {
            analyze(file) {
                val diagnostics = collectErrorDiagnostics(file).filter {
                    helper.shouldAcceptDiagnostic(it.psiElement, it.factoryName)
                }
                val seenInfos = convertToShadowed(diagnostics)
                if (seenInfos == null) {
                    LOG.debug("All conversion attempts failed for ${diagnostics.size} diagnostics")
                    return true
                }

                helper.applyReceivedHighlightInfos(seenInfos, holder)
            }
        } catch (e: Throwable) {
            if (e is ProcessCanceledException) {
                throw e
            }
            LOG.warn("Exception during analyze", e)
            return false
        } finally {
            afterAnalysis()
        }

        return true
    }

    companion object {
        internal val LOG = thisLogger()

        fun create(): KotlinPluginModeShadowingAnalyzerHandler {
            return createPluginModeAwareInstance(
                { K1ShadowingAnalyzerHandler },
                { K2ShadowingAnalyzerHandler }
            )
        }
    }
}

object K1ShadowingAnalyzerHandler : KotlinPluginModeShadowingAnalyzerHandler() {
    override fun applyBeforeProcessingDiagnostic(diagnostic: KaDiagnosticData) = Unit

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


object K2ShadowingAnalyzerHandler : KotlinPluginModeShadowingAnalyzerHandler() {
    override fun applyBeforeProcessingDiagnostic(diagnostic: KaDiagnosticData) {
        diagnostic.psiElement.clearAllKotlinUnresolvedReferenceKinds()
    }
}