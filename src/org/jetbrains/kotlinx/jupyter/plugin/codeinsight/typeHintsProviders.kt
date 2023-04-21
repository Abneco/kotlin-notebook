// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.codeinsight

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.runIf
import org.jetbrains.kotlin.idea.codeInsight.hints.HintType
import org.jetbrains.kotlin.idea.codeInsight.hints.KotlinCallChainHintsProvider
import org.jetbrains.kotlin.idea.codeInsight.hints.KotlinLambdasHintsProvider
import org.jetbrains.kotlin.idea.codeInsight.hints.KotlinReferencesTypeHintsProvider
import org.jetbrains.kotlin.idea.codeInsight.hints.KotlinValuesHintsProvider
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlinx.jupyter.plugin.codeinsight.KotlinNotebookAbstractInlayTypeHintsProvider.Companion.getBindingContext
import org.jetbrains.kotlinx.jupyter.plugin.codeinsight.KotlinNotebookAbstractInlayTypeHintsProvider.Companion.psiHostChainHintsRegistry
import org.jetbrains.kotlinx.jupyter.plugin.codeinsight.KotlinNotebookAbstractInlayTypeHintsProvider.Companion.putBindingContext
import org.jetbrains.kotlinx.jupyter.plugin.file.getKtFileStartOffset
import org.jetbrains.kotlinx.jupyter.plugin.file.getNotebookCompleteAnalysisArea
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.isEitherSymmetricallyContainedRange
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.plugins.notebooks.jupyter.psi.impl.JupyterPsiCellImpl


class NotebookLambdaTypeHintsProvider: KotlinNotebookAbstractInlayTypeHintsProvider<KotlinLambdasHintsProvider.Settings>() {
    private val backingKtLambdaProvider = KotlinLambdasHintsProvider()

    override val key: SettingsKey<KotlinLambdasHintsProvider.Settings> = backingKtLambdaProvider.key

    override fun createConfigurable(settings: KotlinLambdasHintsProvider.Settings): ImmediateConfigurable {
        return backingKtLambdaProvider.createConfigurable(settings)
    }

    override val name: String = backingKtLambdaProvider.name

    override val group: InlayGroup = backingKtLambdaProvider.group

    override fun isElementSupported(resolved: HintType?, settings: KotlinLambdasHintsProvider.Settings): Boolean =
        backingKtLambdaProvider.isElementSupported(resolved, settings)

    override fun isHintSupported(hintType: HintType): Boolean = backingKtLambdaProvider.isHintSupported(hintType)

    override fun createSettings() = backingKtLambdaProvider.createSettings()

    override val properTarget: String = "lambdas"

    override fun preparePreview(editor: Editor, file: PsiFile, settings: KotlinLambdasHintsProvider.Settings) = Unit
    override val previewText: String? = null
}


class KotlinNotebookReferencesTypeHintsProvider : KotlinNotebookAbstractInlayTypeHintsProvider<KotlinReferencesTypeHintsProvider.Settings>() {
    private val backingKtProvider = KotlinReferencesTypeHintsProvider()

    override val key: SettingsKey<KotlinReferencesTypeHintsProvider.Settings> = backingKtProvider.key
    override val name = backingKtProvider.name
    override val group: InlayGroup
        get() = InlayGroup.TYPES_GROUP

    override val properTarget: String = "types"

    override fun createConfigurable(settings: KotlinReferencesTypeHintsProvider.Settings): ImmediateConfigurable
        = backingKtProvider.createConfigurable(settings)

    override fun createSettings() = backingKtProvider.createSettings()

    override fun isElementSupported(resolved: HintType?, settings: KotlinReferencesTypeHintsProvider.Settings): Boolean
        = backingKtProvider.isElementSupported(resolved, settings)

    override fun isHintSupported(hintType: HintType): Boolean = backingKtProvider.isHintSupported(hintType)

    override val previewText: String? = null

    override fun preparePreview(editor: Editor, file: PsiFile, settings: KotlinReferencesTypeHintsProvider.Settings) = Unit
}

class NotebookValuesHintProvider: KotlinNotebookAbstractInlayTypeHintsProvider<KotlinValuesHintsProvider.Settings>() {
    private val backingKtProvider = KotlinValuesHintsProvider()

    override fun isElementSupported(resolved: HintType?, settings: KotlinValuesHintsProvider.Settings): Boolean
        = backingKtProvider.isElementSupported(resolved, settings)

    override fun createSettings(): KotlinValuesHintsProvider.Settings = backingKtProvider.createSettings()
    override val name: String = backingKtProvider.name
    override val key: SettingsKey<KotlinValuesHintsProvider.Settings> = backingKtProvider.key

    override fun createConfigurable(settings: KotlinValuesHintsProvider.Settings): ImmediateConfigurable
        = backingKtProvider.createConfigurable(settings)

    override val properTarget: String
        get() = "ranges"

    override val description: String? = null

    override fun preparePreview(editor: Editor, file: PsiFile, settings: KotlinValuesHintsProvider.Settings) = Unit
}

class NotebookChainCallHintProvider : KotlinCallChainHintsProvider() {
    companion object {
        private val logger = thisLogger()
    }

    override val previewText: String = ""

    override val description: String = "${super.description} in Kotlin Notebook"
    private var lastShouldLimitOptionValue: Boolean = false

    override fun isLanguageSupported(language: Language): Boolean = KotlinNotebookAbstractInlayTypeHintsProvider.isLanguageSupported(language)

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: Settings, sink: InlayHintsSink): InlayHintsCollector? {
        val project = file.project
        val defaultCollector = super.getCollectorFor(file, editor, settings, sink)

        return object : FactoryInlayHintsCollector(editor) {
            private val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)
            private val optionsProvider = KotlinNotebookProjectOptionsProvider.getInstance(project)
            private val injectedLanguageManager = InjectedLanguageManager.getInstance(file.project)

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (file.project.service<DumbService>().isDumb) return true
                if (element is KtFile) return defaultCollector?.collect(element, editor, sink) ?: true
                if (element !is JupyterPsiCellImpl) return true
                val ktFile = tryGetInjectedKtFileIfPossibleOrProvided(element, project) as? PsiFile ?: return true

                val modificationArea = document?.getNotebookCompleteAnalysisArea()

                val registry = KotlinNotebookAbstractInlayTypeHintsProvider.getOrCreateChainCallTypeHintsRegistry(element)
                val fileOffset = element.getKtFileStartOffset(injectedLanguageManager) ?: return true
                                                // lhs.contains(rhs) || rhs.contains(rhs)
                if (modificationArea != null && !isEitherSymmetricallyContainedRange(element.textRange, modificationArea)) {
                    lastShouldLimitOptionValue = optionsProvider.state.shouldLimitTypeHintsByActiveCell
                    if (lastShouldLimitOptionValue) return true
                    try {
                        registry.entries.forEach { (el, data) ->
                            if (el !is KtQualifiedExpression) return@forEach
                            val c = el.getBindingContext() ?: return@forEach // getTypeComputationContext(el)
                            val withTypes = data.mapNotNull { it.first.getType(c)?.let { t -> ExpressionWithType(it.first, t)} }
                            // if file is valid
                            addInlayElementsToSink(c, withTypes, sink, factory, offset = fileOffset)
                        }
                    } catch (e: Throwable) {
                        if (e is ProcessCanceledException) {
                            throw e
                        }
                        logger.warn("Error during applying type hints from registry", e)
                        return true
                    }
                    return true
                }

                return KotlinNotebookAbstractInlayTypeHintsProvider.traverseElementsAndApplyAction(ktFile) { elem ->
                    processInlayElements(elem, settings, sink, factory,
                                         offset = fileOffset)
                    return@traverseElementsAndApplyAction true
                }
            }
        }
    }

    override fun addInlayElementsAdapter(
        context: BindingContext,
        elements: List<ExpressionWithType<KotlinType>>,
        sink: InlayHintsSink,
        factory: PresentationFactory,
        offset: Int
    ) {
        val host = elements.firstOrNull()?.expression?.let {
            val manager = InjectedLanguageManager.getInstance(it.project)
            manager.getInjectionHost(it.containingFile)
        }
        val topMostExpression = elements.first().expression
        val registry = runIf(host != null) {
            host!!.getUserData(psiHostChainHintsRegistry)
        }
        if (host != null && !lastShouldLimitOptionValue) {
            registry?.put(topMostExpression, elements.map { Pair(it.expression, it.type.getInlayPresentation(it.expression, factory, host.project, context)) })
        }
        topMostExpression.putBindingContext(context)

        super.addInlayElementsAdapter(context, elements, sink, factory, offset)
    }
}