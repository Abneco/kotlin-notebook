// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.refactoring

import com.intellij.injected.editor.DocumentWindow
import com.intellij.injected.editor.EditorWindow
import com.intellij.jupyter.core.jupyter.helper.notebookFileOrNull
import com.intellij.kotlin.jupyter.core.editor.find.KotlinNotebookElementFindUsagesHandler
import com.intellij.kotlin.jupyter.core.editor.find.NotebookReferenceFinder
import com.intellij.kotlin.jupyter.core.editor.find.isIdentifier
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.NotebookHighlightingService
import com.intellij.kotlin.jupyter.core.editor.refactoring.NotebookRefactoringSupport.isNotebookRefactoringSupported
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.notifications.notebookNotifications
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.util.getNotebookCells
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.notebooks.visualization.getCell
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.impl.FinishMarkAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.ImaginaryEditor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.NotNullList
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.utils.addIfNotNull


class NotebookMemberInplaceRenamer(
    substituted: PsiElement,
    elementToRename: PsiNamedElement,
    editor: Editor,
    private val originalHostInvocation: PsiLanguageInjectionHost?
) : MemberInplaceRenamer(elementToRename, elementToRename, editor) {
    private val originalElement: PsiElement = substituted
    private val isSameScope = originalElement.containingFile == elementToRename.containingFile
    private var foundRefsSize: Int = 0
    private val prevClassData = myElementToRename.containingFile.getUserData(NotebookReferenceFinder.CELL_CLASS_NAME)
    private val fileSuffix: String get() = JupyterCompilerService.getInstance(originalElement.project).fileSuffix
    private val topLevelDocument = when (val d = myEditor.document) {
        is DocumentWindow -> d.delegate
        else -> d
    }

    override fun performRenameInner(element: PsiElement?, newName: String?) {
        super.performRenameInner(element, newName)
        if (element != null && newName?.isNotEmpty() == true && prevClassData != null) {
            element.containingFile.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, prevClassData)
        }
    }

    override fun getNameIdentifier(): PsiElement? {
        return when (val elem = myElementToRename) {
            is KtClass -> elem.nameIdentifier
            is KtProperty -> elem.nameIdentifier
            is KtNamedFunction -> elem.nameIdentifier
            is PsiNameIdentifierOwner -> elem.nameIdentifier
            else -> elem
        }
    }

    override fun collectRefs(referencesSearchScope: SearchScope?): MutableCollection<PsiReference> {
        return super.collectRefs(referencesSearchScope)
    }

    override fun createRenameProcessor(element: PsiElement, newName: String): RenameProcessor {
        return object : MyRenameProcessor(element, newName) {
            private val findUsagesNotebookHandler = KotlinNotebookElementFindUsagesHandler(element)
            private val injectedManager = InjectedLanguageManager.getInstance(element.project)
            private val elementHost = injectedManager.getInjectionHost(element.containingFile)
            private var adjustmentTextRange: Collection<TextRange>? = null
            private val topLevelEditor = when (myEditor) {
                is EditorWindow -> (myEditor as EditorWindow).delegate
                else -> myEditor
            }
            private val notebookHighlightingService = topLevelEditor.notebookFileOrNull?.let {
                NotebookHighlightingService.getForFile(element.project, it)
            }

            override fun performRefactoring(usages: Array<out UsageInfo>) {
                if (foundRefsSize > 0) {
                    element.project.notebookNotifications.showRerunActionNeeded()
                    val hostFile = injectedManager.getTopLevelFile(element)
                    if (adjustmentTextRange != null) {
                        notebookHighlightingService?.dataController?.update {
                            notebookChangedCellIndex = hostFile?.getNotebookCells()?.indexOf(originalHostInvocation)
                            renamingEnclosedRange = adjustmentTextRange
                        }
                    }
                }
                runReadAction {
                    super.performRefactoring(usages)
                }
            }

            override fun findUsages(): Array<UsageInfo> {
                val size = foundRefsSize
                val notificationUtility = element.project.notebookNotifications
                do { // todo: might be slow (?)
                   val ans = findUsagesNotebookHandler.findReferencesToHighlight(myElementToRename, element.resolveScope).map {
                        it.toMoveUsageInfo()
                    }
                    if (ans.isEmpty()) {
                        notificationUtility.showRefactoringExistingUsagesMessage(size)
                        return ans.toTypedArray()
                    }
                    if (size == ans.size) {
                        notificationUtility.showRerunActionNeeded()
                        val targetHostRanges = mutableSetOf<TextRange>()
                        val targetHostIndxs = mutableSetOf<Int>()
                        elementHost?.textRange?.let {
                            targetHostRanges.add(it)
                        }
                        ans.forEach {
                            val el = it.element?.containingFile
                            if (el != null) {
                                injectedManager.getInjectionHost(el)?.textRange?.let { host ->
                                    targetHostIndxs.addIfNotNull(
                                        topLevelEditor.getCell(
                                            topLevelDocument.getLineNumber(host.startOffset)
                                        ).ordinal
                                    )
                                    targetHostRanges.add(host)
                                }
                            }
                        }
                        if (targetHostRanges.isNotEmpty()) {
                            adjustmentTextRange = targetHostRanges
                            notebookHighlightingService?.dataController?.notebookRangesQueuedForHL?.addAll(
                                targetHostIndxs
                            )
                        }
                        return ans.toTypedArray()
                    }
                } while (true)
            }
        }
    }

    override fun getSelectedInEditorElement(
        nameIdentifier: PsiElement?,
        refs: Collection<PsiReference>,
        stringUsages: Collection<Pair<PsiElement, TextRange>>,
        offset: Int
    ): PsiElement {
        // todo: look in MemberInPlaceRenamer
        return if (nameIdentifier == myElementToRename && isSameScope) myElementToRename
                else originalElement.getParentOfType<KtReferenceExpression>(false) ?: originalElement
    }

    override fun getRangeToRename(element: PsiElement): TextRange {
        return when (isNotebookRefactoringSupported(element)) {
            true -> {
                val name = originalElement.text
                var idRange: TextRange? = null
                element.accept(object : PsiRecursiveElementVisitor() {
                    override fun visitElement(element: PsiElement) {
                        if (idRange != null) return
                        if (element is LeafPsiElement &&
                            element.isIdentifier() &&
                            element.text == name) {
                            idRange = element.textRangeInParent
                            return
                        }
                        super.visitElement(element)
                    }
                })
                idRange ?: element.textRangeInParent
            }
            else -> super.getRangeToRename(element)
        }
    }

    override fun performInplaceRefactoring(nameSuggestions: LinkedHashSet<String>?): Boolean {
        if (myEditor is ImaginaryEditor && myEditor.getUserData(INPLACE_RENAME_ALLOWED) != true) return false
        myNameSuggestions = nameSuggestions
        if (InjectedLanguageUtil.isInInjectedLanguagePrefixSuffix(myElementToRename)) {
            return false
        }

        val references =
            KotlinNotebookElementFindUsagesHandler(myElementToRename).findReferencesToHighlight(myElementToRename, myElementToRename.useScope)
        val scope = checkLocalScope() ?: return false

        val containingFile = scope.containingFile
            ?: return false

        if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, containingFile)) return true

        myEditor.putUserData(INPLACE_RENAMER, this)
        foundRefsSize = references.size

        val stringUsages: MutableList<Pair<PsiElement, TextRange>> = NotNullList()
        collectAdditionalElementsToRename(stringUsages)

        return try {
            buildTemplateAndStart(references, stringUsages, scope, containingFile)
        } catch (e: Throwable) {
            myEditor.putUserData(INPLACE_RENAMER, null)
            FinishMarkAction.finish(myProject, myEditor, myMarkAction)
            foundRefsSize = 0

            if (e is ProcessCanceledException) {
                throw e
            }

            notebookLogger().warn("Error occurred during template", e)
            return false
        }
    }

    override fun getVariable(): PsiNamedElement? {
        val clazz = if (myElementToRename != null) myElementToRename::class.java else PsiNameIdentifierOwner::class.java
        myElementToRename?.let { toRename ->
            if (myOldName == toRename.name) return toRename
            if (myRenameOffset != null)
            // todo: change to findElements of Identifier ElementType
                return PsiTreeUtil.findElementOfClassAtRange(
                    toRename.containingFile,
                    myRenameOffset.startOffset,
                    myRenameOffset.endOffset,
                    clazz
                )
        }

        if (myRenameOffset != null) {
            PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.document)?.let { psiFile ->
                return PsiTreeUtil.findElementOfClassAtRange(
                    psiFile, myRenameOffset.startOffset, myRenameOffset.endOffset,
                    clazz
                )
            }
        }
        return myElementToRename
    }


    override fun isReferenceAtCaret(selectedElement: PsiElement?, ref: PsiReference?, offset: Int): Boolean {
        return if (selectedElement?.containingFile?.name?.endsWith(fileSuffix) == true)
            super.isReferenceAtCaret(selectedElement, ref)
        else super.isReferenceAtCaret(selectedElement, ref, offset)
    }
}