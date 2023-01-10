// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.codeinsight

//import com.intellij.ui.layout.panel
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.HorizontalConstraints
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.RecursivelyUpdatingRootPresentation
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.SyntaxTraverser
import org.jetbrains.kotlin.idea.codeInsight.hints.HintType
import org.jetbrains.kotlin.idea.codeInsight.hints.InlayInfoDetails
import org.jetbrains.kotlin.idea.codeInsight.hints.KotlinAbstractHintsProvider
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinBundle
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.isEitherSymmetricallyContainedRange
import org.jetbrains.plugins.notebooks.jupyter.JupyterLanguage
import org.jetbrains.plugins.notebooks.jupyter.nbformat.CELL_MARKER
import org.jetbrains.plugins.notebooks.jupyter.psi.impl.JupyterPsiCellImpl

typealias PsiHostChainCallTypeHintsRegistry = MutableMap<PsiElement, List<Pair<PsiElement, InlayPresentation>>>
// sourceElem -> [typeHint -> []]
typealias PsiHostTypeHintsRegistry = MutableMap<PsiElement, MutableMap<HintType, MutableCollection<InlayInfoDetails>?>>

abstract class KotlinNotebookAbstractInlayTypeHintsProvider<T: Any> : KotlinAbstractHintsProvider<T>() {
    override fun isLanguageSupported(language: Language): Boolean {
        return KotlinNotebookAbstractInlayTypeHintsProvider.isLanguageSupported(language)
    }

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: T, sink: InlayHintsSink): InlayHintsCollector? {
        return object : FactoryInlayHintsCollector(editor) {
            private val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)!!
            //private val shouldStoreData = (file.getNotebookCellList()?.size ?: 0) > 30

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                val project = editor.project ?: element.project
                if (DumbService.isDumb(project) || element !is JupyterPsiCellImpl || !element.isValid) return true

                val modificationArea = document.getNotebookModificationArea()
                val registry = getOrCreateTypeHintsRegistry(element)
                val hostOffset = element.textOffset


                if (modificationArea != null && !isEitherSymmetricallyContainedRange(element.textRange, modificationArea)) {
                    registry.entries.forEach { (el, data) ->
                        val resolved = data.keys.filter { isElementSupported(it, settings) }.ifEmpty { return@forEach }
                        resolved.forEach { hintType ->
                            addInlayElementToSink(el, project,
                                                  hintType, sink,
                                                  factory, this@KotlinNotebookAbstractInlayTypeHintsProvider,
                                                  hintsPriority, hintsArePlacedAtTheEndOfLine, hostOffset,
                                                  registry, RegistryMode.Apply)
                        }
                    }
                    return true
                }

                val properFileElement = tryGetInjectedKtFileIfPossibleOrProvided(element, project)

               return traverseElementsAndApplyAction(properFileElement) { elem ->
                    val resolved = HintType.resolve(elem).ifEmpty { return@traverseElementsAndApplyAction true }
                    val f = factory
                    resolved.forEach { hintType ->
                        registry.putIfAbsent(elem, mutableMapOf())
                        if (isElementSupported(hintType, settings)) {
                            addInlayElementToSink(elem, project,
                                                  hintType, sink,
                                                  f, this@KotlinNotebookAbstractInlayTypeHintsProvider,
                                                  hintsPriority, hintsArePlacedAtTheEndOfLine, hostOffset, registry, RegistryMode.Store)
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
        internal val psiHostChainHintsRegistry = Key.create<PsiHostChainCallTypeHintsRegistry>("jupyter.kotlin.inlay.hints.chain.call.registry")
        internal val psiHostHintsRegistry = Key.create<PsiHostTypeHintsRegistry>("jupyter.kotlin.inlay.hints.registry")
        internal val psiBindingContext = Key.create<BindingContext>("jupyter.kotlin.inlay.hints.binding.context")
        internal enum class RegistryMode {
            Apply,
            Store
        }

        internal fun PsiElement.putBindingContext(bindingContext: BindingContext) =
            putUserData(psiBindingContext, bindingContext)

        internal fun PsiElement.getBindingContext(): BindingContext? =
            if (getUserData(psiBindingContext) != null) {
                synchronized(this) {
                    getUserData(psiBindingContext)
                }
            } else null

        internal fun getOrCreateChainCallTypeHintsRegistry(host: PsiLanguageInjectionHost): PsiHostChainCallTypeHintsRegistry = synchronized(host) {
            val stored = host.getUserData(psiHostChainHintsRegistry)
            if (stored == null) {
                host.putUserData(psiHostChainHintsRegistry, mutableMapOf())
                host.getUserData(psiHostChainHintsRegistry)!!
            } else stored
        }

        internal fun getOrCreateTypeHintsRegistry(host: PsiLanguageInjectionHost): PsiHostTypeHintsRegistry = synchronized(host) {
            val stored = host.getUserData(psiHostHintsRegistry)
            if (stored == null) {
                host.putUserData(psiHostHintsRegistry, mutableMapOf())
                host.getUserData(psiHostHintsRegistry)!!
            } else stored
        }

        internal fun addInlayElementToSink(contextElement: PsiElement, project: Project,
                                           hintType: HintType, sink: InlayHintsSink,
                                           factory: PresentationFactory,
                                           provider: InlayHintsProvider<*>,
                                           hintsPriority: Int,
                                           isEndOfTheLine: Boolean,
                                           hostOffset: Int, registry: PsiHostTypeHintsRegistry,
                                           registryMode: RegistryMode,
                                           inlayPresentation: InlayPresentation? = null) {
            val registryDetailsInfo = registry[contextElement]?.getOrPut(hintType) { mutableListOf() }
            val detailsInfo = if (!registryDetailsInfo.isNullOrEmpty() && registryMode == RegistryMode.Apply) {
                registryDetailsInfo
            } else hintType.provideHintDetails(contextElement).also {
            //val detailsInfo = hintType.provideHintDetails(contextElement).also {
                registryDetailsInfo?.addAll(it)
                registry[contextElement]?.put(hintType, registryDetailsInfo)
            }

            detailsInfo.forEach { details ->
                val p = PresentationAndSettings(
                    inlayPresentation ?: getInlayPresentationForInlayInfoDetails(contextElement, hintType, details, factory, project, provider),
                    details.inlayInfo.offset + hostOffset + markerShift,
                    details.inlayInfo.relatesToPrecedingText
                )
                val horizontalConstraints = HorizontalConstraints(hintsPriority, p.relatesToPrecedingText, isEndOfTheLine)
                sink.addInlineElement(p.offset, RecursivelyUpdatingRootPresentation(p.presentation), horizontalConstraints)
            }
        }

        internal fun Document?.getNotebookModificationArea(): TextRange? =
            if (this?.getUserData(NotebookHighlightingUtilityObject.NOTEBOOK_DOCUMENT_TARGET_ANALYSIS_RANGE) != null) {
                synchronized(this) { this.getUserData(NotebookHighlightingUtilityObject.NOTEBOOK_DOCUMENT_TARGET_ANALYSIS_RANGE) }
            } else null

        const val markerShift = CELL_MARKER.length + 1
        fun isLanguageSupported(language: Language): Boolean = language == JupyterLanguage

        inline fun traverseElementsAndApplyAction(rootElement: PsiElement, crossinline action: (PsiElement) -> Boolean): Boolean {
            val traverser = SyntaxTraverser.psiTraverser(rootElement)
            try {
                for (element in traverser.preOrderDfsTraversal()) {
                    if (!action(element)) return false
                }
            } catch (_: Throwable) { // ignore any 
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

