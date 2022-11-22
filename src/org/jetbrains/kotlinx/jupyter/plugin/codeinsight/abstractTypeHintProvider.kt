// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.codeinsight

//import com.intellij.ui.layout.panel
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.HorizontalConstraints
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.RecursivelyUpdatingRootPresentation
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.SyntaxTraverser
import org.jetbrains.kotlin.idea.codeInsight.hints.HintType
import org.jetbrains.kotlin.idea.codeInsight.hints.KotlinAbstractHintsProvider
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinBundle
import org.jetbrains.plugins.notebooks.jupyter.JupyterLanguage
import org.jetbrains.plugins.notebooks.jupyter.nbformat.CELL_MARKER
import org.jetbrains.plugins.notebooks.jupyter.psi.impl.JupyterPsiCellImpl

typealias PsiHostTypeHintsRegistry = MutableMap<PsiElement, List<Pair<PsiElement, InlayPresentation>>>

abstract class KotlinNotebookAbstractInlayTypeHintsProvider<T: Any> : KotlinAbstractHintsProvider<T>() {
    override fun isLanguageSupported(language: Language): Boolean {
        return KotlinNotebookAbstractInlayTypeHintsProvider.isLanguageSupported(language)
    }

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: T, sink: InlayHintsSink): InlayHintsCollector? {
        return object : FactoryInlayHintsCollector(editor) {
            private val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)!!

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                val project = editor.project ?: element.project
                if (DumbService.isDumb(project) || element !is JupyterPsiCellImpl) return true

                // todo: store previous results
                //val modificationArea = if (document.getUserData(NotebookInjectedCodeUtility.NOTEBOOK_DOCUMENT_IGNORE_ANALYSIS_RANGE) != null) {
                //    synchronized(document) { document.getUserData(NotebookInjectedCodeUtility.NOTEBOOK_DOCUMENT_IGNORE_ANALYSIS_RANGE) }
                //} else null
                //if (modificationArea != null && !element.textRange.contains(modificationArea)) return false

                val properFileElement = tryGetInjectedKtFileIfPossibleOrProvided(element, project)

               return traverseElementsAndApplyAction(properFileElement) { elem ->
                    val resolved = HintType.resolve(elem).ifEmpty { return@traverseElementsAndApplyAction true }
                    val f = factory
                    resolved.forEach { hintType ->
                        if (isElementSupported(hintType, settings)) {
                            hintType.provideHintDetails(elem).forEach { details ->
                                val p = PresentationAndSettings(
                                    getInlayPresentationForInlayInfoDetails(elem, hintType, details, f, project, this@KotlinNotebookAbstractInlayTypeHintsProvider),
                                    details.inlayInfo.offset + element.textOffset + markerShift,
                                    details.inlayInfo.relatesToPrecedingText
                                )
                                val horizontalConstraints = HorizontalConstraints(hintsPriority, p.relatesToPrecedingText, hintsArePlacedAtTheEndOfLine)
                                sink.addInlineElement(p.offset, RecursivelyUpdatingRootPresentation(p.presentation), horizontalConstraints)
                            }
                        }
                    }
                   return@traverseElementsAndApplyAction true
                }
            }
        }
    }

    abstract val properTarget: String

    override val description: String?
        get() = JupyterKotlinBundle.message("inlay.hint.description.prefix", properTarget)

    companion object {
        internal val psiHostHintsRegistry = Key.create<PsiHostTypeHintsRegistry>("jupyter.kotlin.inlay.hints.registry")

        internal fun getOrCreateTypeHintsRegistry(host: PsiLanguageInjectionHost): PsiHostTypeHintsRegistry = synchronized(host) {
            val stored = host.getUserData(psiHostHintsRegistry)
            if (stored == null) {
                host.putUserData(psiHostHintsRegistry, mutableMapOf())
                host.getUserData(psiHostHintsRegistry)!!
            } else stored
        }

        const val markerShift = CELL_MARKER.length + 1
        fun isLanguageSupported(language: Language): Boolean = language == JupyterLanguage

        inline fun traverseElementsAndApplyAction(rootElement: PsiElement, crossinline action: (PsiElement) -> Boolean): Boolean {
            val traverser = SyntaxTraverser.psiTraverser(rootElement)
            try {
                for (element in traverser.preOrderDfsTraversal()) {
                    if (!action(element)) return false
                }
            } catch (_: Exception) {
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

