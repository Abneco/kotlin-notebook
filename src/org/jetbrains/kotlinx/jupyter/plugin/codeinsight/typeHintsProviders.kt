// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.codeinsight

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.util.asSafely
import org.jetbrains.kotlin.idea.codeInsight.hints.HintType
import org.jetbrains.kotlin.idea.codeInsight.hints.KotlinCallChainHintsProvider
import org.jetbrains.kotlin.idea.codeInsight.hints.KotlinLambdasHintsProvider
import org.jetbrains.kotlin.idea.codeInsight.hints.KotlinReferencesTypeHintsProvider
import org.jetbrains.kotlin.idea.codeInsight.hints.KotlinValuesHintsProvider
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.types.KotlinType
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
    private val shiftMargin = KotlinNotebookAbstractInlayTypeHintsProvider.markerShift

    override val previewText: String = ""

    override val description: String = "${super.description} in Kotlin Notebook"

    override fun preparePreview(editor: Editor, file: PsiFile, settings: Settings) {
        super.preparePreview(editor, file, settings)
    }

    override fun isLanguageSupported(language: Language): Boolean = KotlinNotebookAbstractInlayTypeHintsProvider.isLanguageSupported(language)

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: Settings, sink: InlayHintsSink): InlayHintsCollector? {
        val project = file.project
        val defaultCollector = super.getCollectorFor(file, editor, settings, sink)

        return object : FactoryInlayHintsCollector(editor) {
            private val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)!!

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (file.project.service<DumbService>().isDumb) return true
                if (element is KtFile) return defaultCollector?.collect(element, editor, sink) ?: true
                if (element !is JupyterPsiCellImpl) return true
                //val modificationArea = if (document.getUserData(NotebookInjectedCodeUtility.NOTEBOOK_DOCUMENT_IGNORE_ANALYSIS_RANGE) != null) {
                //    synchronized(document) { document.getUserData(NotebookInjectedCodeUtility.NOTEBOOK_DOCUMENT_IGNORE_ANALYSIS_RANGE) }
                //} else null
                //
                //if (modificationArea != null && !modificationArea.contains(element.textRange)) return true

                val ktFile = tryGetInjectedKtFileIfPossibleOrProvided(element, project) as? PsiFile ?: return true

                return KotlinNotebookAbstractInlayTypeHintsProvider.traverseElementsAndApplyAction(ktFile) { elem ->
                    val topmostDotQualifiedExpression =
                        (elem.safeAs(dotQualifiedClass) ?: elem.safeAs(KtSafeQualifiedExpression::class.java))
                        ?.takeIf { it.getParentDotQualifiedExpression() == null }
                        ?: return@traverseElementsAndApplyAction true
                    val targetClass = if (elem is KtSafeQualifiedExpression) KtSafeQualifiedExpression::class.java else dotQualifiedClass
                    data class ExpressionWithType(val expression: PsiElement, val type: KotlinType)

                    val context = getTypeComputationContext(topmostDotQualifiedExpression)

                    var someTypeIsUnknown = false
                    val reversedChain =
                        generateSequence<PsiElement>(topmostDotQualifiedExpression) {
                            it.skipParenthesesAndPostfixOperatorsDown()?.safeAs(targetClass)?.getReceiver()
                        }
                            .drop(1) // Except last to avoid builder.build() which has obvious type
                            .filter { it.nextSibling.asSafely<PsiWhiteSpace>()?.textContains('\n') == true }
                            .map { it to it.getType(context) }
                            .takeWhile { (_, type) -> (type != null).also { if (!it) someTypeIsUnknown = true } }
                            .map { (expression, type) -> ExpressionWithType(expression, type!!) }
                            .windowed(2, partialWindows = true) { it.first() to it.getOrNull(1) }
                            .filter { (expressionWithType, prevExpressionWithType) ->
                                if (prevExpressionWithType == null) {
                                    // Show type for expression in call chain on the first line only if it's dot qualified
                                    dotQualifiedClass.isInstance(expressionWithType.expression.skipParenthesesAndPostfixOperatorsDown())
                                } else {
                                    expressionWithType.type != prevExpressionWithType.type ||
                                            !targetClass.isInstance(prevExpressionWithType.expression.skipParenthesesAndPostfixOperatorsDown())
                                }
                            }
                            .map { it.first }
                            .toList()
                    if (someTypeIsUnknown) return@traverseElementsAndApplyAction true

                    if (reversedChain.asSequence().distinctBy { it.type }.count() < settings.uniqueTypeCount) return@traverseElementsAndApplyAction true

                    for ((expression, type) in reversedChain) {
                        sink.addInlineElement(
                            expression.textRange.endOffset + element.textOffset + shiftMargin,
                            true,
                            type.getInlayPresentation(expression, factory, file.project, context),
                            false
                        )
                    }
                    return@traverseElementsAndApplyAction true
                }
            }
        }
    }

    private fun <T> Any.safeAs(clazz: Class<T>) = if (this::class.java == clazz) clazz.cast(this) else null
}