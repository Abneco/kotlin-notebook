// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.actions.refactor

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.impl.FinishMarkAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.ImaginaryEditor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
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
import org.jetbrains.kotlinx.jupyter.plugin.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.actions.refactor.NotebookNotificationUtility.showExistingUsagesMessage
import org.jetbrains.kotlinx.jupyter.plugin.actions.refactor.NotebookNotificationUtility.showRerunActionNeeded
import org.jetbrains.kotlinx.jupyter.plugin.actions.refactor.NotebookRefactoringSupport.isNotebookRefactoringSupported
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.ANALYZER_PASS_INJECTED_INFO_HOLDER_KEY
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.RenamingEnclosedRange
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.KotlinNotebookElementFindUsagesHandler
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.NotebookReferenceFinder
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.isIdentifier


class NotebookMemberInplaceRenamer(
    substituted: PsiElement,
    elementToRename: PsiNamedElement,
    editor: Editor
) : MemberInplaceRenamer(elementToRename, elementToRename, editor) {
    private val originalElement: PsiElement = substituted
    private val isSameScope = originalElement.containingFile == elementToRename.containingFile
    private var foundRefsSize: Int = 0
    private val prevClassData = myElementToRename.containingFile.getUserData(NotebookReferenceFinder.CELL_CLASS_NAME)
    private val fileSuffix: String get() = JupyterCompilerService.getInstance(originalElement.project).fileSuffix

    override fun performRenameInner(element: PsiElement?, newName: String?) {
        super.performRenameInner(element, newName)
        if (element != null && newName?.isNotEmpty() == true && prevClassData != null) {
            //invalidateStoredUserData(element.containingFile, null)
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

            override fun performRefactoring(usages: Array<out UsageInfo>) {
                if (foundRefsSize > 0) {
                    showRerunActionNeeded(myProject)
                    val hostFile = injectedManager.getTopLevelFile(element)
                    if (adjustmentTextRange != null) {
                        FileDocumentManager.getInstance().getDocument(hostFile.virtualFile)
                            ?.putUserData(RenamingEnclosedRange, adjustmentTextRange)
                    }
                }
                runReadAction {
                    super.performRefactoring(usages)
                }
            }

            override fun findUsages(): Array<UsageInfo> {
                val size = foundRefsSize
                do { // todo: might be slow (?)
                   val ans = findUsagesNotebookHandler.findReferencesToHighlight(myElementToRename, element.resolveScope).map {
                        it.toMoveUsageInfo()
                    }
                    if (ans.isEmpty()) {
                        showExistingUsagesMessage(myProject, size)
                        return ans.toTypedArray()
                    }
                    if (size == ans.size) {
                        showRerunActionNeeded(myProject)
                        val targetHostRanges = mutableListOf<TextRange>()
                        elementHost?.textRange?.let {
                            targetHostRanges.add(it)
                        }
                        ans.forEach {
                            val el = it.element?.containingFile
                            if (el != null) {
                                injectedManager.getInjectionHost(el)?.textRange?.let { host ->
                                    targetHostRanges.add(host)
                                }
                            }
                        }
                        if (targetHostRanges.isNotEmpty()) {
                            adjustmentTextRange = targetHostRanges
                        }
                        return ans.toTypedArray()
                    }
                } while (true)
            }
        }
    }

    override fun getSelectedInEditorElement(
        nameIdentifier: PsiElement?,
        refs: MutableCollection<out PsiReference>?,
        stringUsages: MutableCollection<out Pair<PsiElement, TextRange>>?,
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
        if (myEditor is ImaginaryEditor && myEditor.getUserData(INPLACE_RENAME_ALLOWED) !== java.lang.Boolean.TRUE) return false
        myNameSuggestions = nameSuggestions
        if (InjectedLanguageUtil.isInInjectedLanguagePrefixSuffix(myElementToRename)) {
            return false
        }

        val references =
            KotlinNotebookElementFindUsagesHandler(myElementToRename).findReferencesToHighlight(myElementToRename, myElementToRename.useScope)
                //.filter {
                //    it.element.containingFile == originalElement.containingFile
                //}
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
            throw e
        }
    }

    private fun invalidateStoredUserData(containingFile: PsiFile, references: Collection<out PsiReference>?) {
        containingFile.putUserData(ANALYZER_PASS_INJECTED_INFO_HOLDER_KEY, null)
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