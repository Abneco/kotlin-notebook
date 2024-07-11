// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.codeInsight

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.HorizontalConstraints
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.RecursivelyUpdatingRootPresentation
import com.intellij.notebooks.jupyter.core.jupyter.JupyterLanguage
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.SyntaxTraverser
import org.jetbrains.kotlin.idea.codeInsight.hints.HintType
import org.jetbrains.kotlin.idea.codeInsight.hints.KotlinAbstractHintsProvider
import org.jetbrains.kotlin.idea.codeInsight.hints.getInlayPresentationForInlayInfoDetails
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlinx.jupyter.plugin.editor.codeInsight.PsiHostTypeHintsRegistry.Companion.getOrCreateTypeHintsRegistry
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingService
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.isEitherSymmetricallyContainedRange
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.util.getKtFileStartOffset
import org.jetbrains.plugins.notebooks.core.impl.file.notebookOrNull
import org.jetbrains.plugins.notebooks.jupyter.psi.impl.JupyterPsiCellImpl


abstract class KotlinNotebookAbstractInlayTypeHintsProvider<T: Any> : KotlinAbstractHintsProvider<T>() {
    override fun isLanguageSupported(language: Language): Boolean {
        return Companion.isLanguageSupported(language)
    }

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: T, sink: InlayHintsSink): InlayHintsCollector? {
        val project = file.project

        return object : FactoryInlayHintsCollector(editor) {
            private val optionsProvider = KotlinNotebookProjectOptionsProvider.getInstance(project)
            private val injectedLanguageManager = InjectedLanguageManager.getInstance(file.project)

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (DumbService.isDumb(project) || element !is JupyterPsiCellImpl || !element.isValid) return true

                val highlightingManager = editor.notebookOrNull?.let {
                    NotebookHighlightingService.getForFile(project, it)
                }
                val modificationArea = highlightingManager?.dataController?.completeHighlightingRange
                val registry = getOrCreateTypeHintsRegistry(element)
                val fileOffset = element.getKtFileStartOffset(injectedLanguageManager) ?: return true
                ProgressManager.checkCanceled()

                val shouldLimit = optionsProvider.shouldLimitTypeHintsByActiveCell
                val registryIsValid = registry.isDataInsideValid

                if (modificationArea != null && registryIsValid && !isEitherSymmetricallyContainedRange(element.textRange, modificationArea)) {
                    if (shouldLimit) return true

                    try {
                        ProgressManager.checkCanceled()

                        if (!element.isValid) {
                            logger.warn("Error during applying type hints from registry: host is invalid")
                            return false
                        }
                        registry.data.entries.forEach { (el, data) ->
                            if (!element.containingFile.virtualFile.isValid || !element.containingFile.isValid) return false
                            val resolved = data.filter { isElementSupported(it, settings) }.ifEmpty { return@forEach }
                            resolved.forEach { hintType ->
                                addInlayElementToSink(
                                    el, project,
                                    hintType, sink,
                                    factory, this@KotlinNotebookAbstractInlayTypeHintsProvider,
                                    hintsPriority, hintsArePlacedAtTheEndOfLine, fileOffset,
                                    registry
                                )
                            }
                        }
                    } catch (e: Throwable) {
                        registry.markInvalid()
                        if (e is ProcessCanceledException) {
                            throw e
                        }
                        logger.warn("Error during applying type hints for ${this::class} from registry", e)
                        return true
                    }
                    return true
                }

                val properFileElement = tryGetInjectedKtFileIfPossibleOrProvided(element, project)

                return traverseElementsAndApplyAction(properFileElement) { elem ->
                    val resolved = HintType.resolve(elem).ifEmpty { return@traverseElementsAndApplyAction true }
                    val f = factory
                    resolved.forEach { hintType ->
                        if (!shouldLimit) {
                            registry.data.putIfAbsent(elem, mutableSetOf())
                        }
                        if (isElementSupported(hintType, settings)) {
                            addInlayElementToSink(
                                elem, project,
                                hintType, sink,
                                f, this@KotlinNotebookAbstractInlayTypeHintsProvider,
                                hintsPriority, hintsArePlacedAtTheEndOfLine, fileOffset, registry
                            )
                        }
                    }
                   return@traverseElementsAndApplyAction true
                }
            }
        }
    }

    abstract val properTarget: String

    override val description: String?
        get() = KotlinNotebookBundle.message("inlay.hint.description.prefix", properTarget)

    companion object {
        private val logger = logger<KotlinNotebookAbstractInlayTypeHintsProvider<*>>()
        private val psiBindingContext = Key.create<BindingContext>("jupyter.kotlin.inlay.hints.binding.context")

        internal fun PsiElement.putBindingContext(bindingContext: BindingContext) =
            putUserData(psiBindingContext, bindingContext)

        internal fun PsiElement.getBindingContext(): BindingContext? =
            if (getUserData(psiBindingContext) != null) {
                synchronized(this) {
                    getUserData(psiBindingContext)
                }
            } else null

        internal fun addInlayElementToSink(
            contextElement: PsiElement, project: Project,
            hintType: HintType, sink: InlayHintsSink,
            factory: PresentationFactory,
            provider: InlayHintsProvider<*>,
            hintsPriority: Int,
            isEndOfTheLine: Boolean,
            injectionOffset: Int, registry: PsiHostTypeHintsRegistry,
            inlayPresentation: InlayPresentation? = null
        ) {
            registry.data[contextElement]?.add(hintType)
            val detailsInfo = hintType.provideHintDetails(contextElement)

            detailsInfo.forEach { details ->
                val p = PresentationAndSettings(
                    inlayPresentation ?: getInlayPresentationForInlayInfoDetails(contextElement, hintType, details, factory, project, provider),
                    details.inlayInfo.offset + injectionOffset,
                    details.inlayInfo.relatesToPrecedingText
                )
                val horizontalConstraints = HorizontalConstraints(hintsPriority, p.relatesToPrecedingText, isEndOfTheLine)
                sink.addInlineElement(p.offset, RecursivelyUpdatingRootPresentation(p.presentation), horizontalConstraints)
            }
        }

        fun isLanguageSupported(language: Language): Boolean = language == JupyterLanguage

        inline fun traverseElementsAndApplyAction(rootElement: PsiElement, crossinline action: (PsiElement) -> Boolean): Boolean {
            val traverser = SyntaxTraverser.psiTraverser(rootElement)
            try {
                for (element in traverser.preOrderDfsTraversal()) {
                    ProgressManager.checkCanceled()
                    if (!action(element)) return false
                }
            } catch (e: Throwable) { // ignore any
                if (e is ProcessCanceledException) {
                    throw e
                }
                return false
            }
            return true
        }
    }
}

internal fun tryGetInjectedKtFileIfPossibleOrProvided(element: PsiElement, project: Project): PsiElement {
    if (element !is PsiLanguageInjectionHost) return element
    val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
    return injectedLanguageManager.getInjectedPsiFiles(element)?.firstOrNull { it.first.containingFile is KtFile }?.first ?: element
}
