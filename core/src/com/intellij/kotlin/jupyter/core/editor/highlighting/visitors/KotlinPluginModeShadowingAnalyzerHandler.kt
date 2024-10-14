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
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.highlighter.AbstractKotlinHighlightVisitor.Companion.suppressHighlight
import org.jetbrains.kotlin.idea.highlighter.AbstractKotlinHighlightVisitor.Companion.unsuppressHighlight
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile


/**
 * Base class for managing analysis outside the focus cell.
 * The handlers are aware of the plugin's operation mode (e.g., K1 or K2).
 *
 * This analysis involves detecting errors and creating a special highlighter for it.
 * All instances of this class should be stateless, ideally, objects.
 */
sealed class KotlinPluginModeShadowingAnalyzerHandler : KotlinPluginModeAwareHandler {
    protected fun prepareForFile(injectedFile: PsiFile) : InjectedFileHighlightingHelper {
        val helper =  InjectedFileHighlightingHelper(injectedFile)
        helper.markTargetHost()

        return helper
    }

    abstract fun performShadowing(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, afterAnalysis: () -> Unit = {}): Boolean

    companion object {
        fun create(): KotlinPluginModeShadowingAnalyzerHandler {
            return createPluginModeAwareInstance(
                { K1ShadowingAnalyzerHandler },
                { K2ShadowingAnalyzerHandler }
            )
        }
    }
}

object K1ShadowingAnalyzerHandler : KotlinPluginModeShadowingAnalyzerHandler() {
    override fun performShadowing(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, afterAnalysis: () -> Unit): Boolean {
        if (file !is KtFile) return true

        val helper = prepareForFile(file)
        val isTargetHost = helper.isCurrentFileTarget

        if (isTargetHost) {
            file.unsuppressHighlight()
            return true
        }

        try {
            val seenInfos = mutableSetOf<HighlightInfo>()
            file.analyzeWithAllCompilerChecks(
                {
                    if (it.severity == Severity.ERROR) {
                        val element = it.psiElement as? KtElement
                        element?.suppressHighlight()
                        if (!helper.shouldAcceptDiagnostic(it)) return@analyzeWithAllCompilerChecks

                        val info = convertToShadowedDeclaration(it)

                        if (info != null) {
                            seenInfos.add(info)
                        } else thisLogger().warn("Cannot convert diagnostic to shadowed: $it")
                    }
                }
            )

            helper.applyReceivedHighlightInfos(seenInfos, holder)
        } catch (e: Throwable) {
            if (e is ProcessCanceledException) {
                throw e
            }
            thisLogger().warn("Exception during analyze", e)
            return false
        } finally {
            afterAnalysis()
        }

        return true
    }
}


object K2ShadowingAnalyzerHandler : KotlinPluginModeShadowingAnalyzerHandler() {
    // not yet supported
    override fun performShadowing(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, afterAnalysis: () -> Unit) : Boolean {
        afterAnalysis()
        return true
    }
}