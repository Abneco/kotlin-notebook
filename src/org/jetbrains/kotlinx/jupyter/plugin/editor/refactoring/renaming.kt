// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.refactoring

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.ide.DataManager
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.util.runIf
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.idea.refactoring.rename.RenameKotlinPropertyProcessor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.scripting.definitions.isScript
import org.jetbrains.kotlinx.jupyter.plugin.editor.codeInsight.NotebookGotoDeclarationProvider
import org.jetbrains.kotlinx.jupyter.plugin.editor.find.KotlinNotebookElementFindUsagesHandler
import org.jetbrains.kotlinx.jupyter.plugin.editor.find.NotebookReferenceFinder.CELL_CLASS_NAME
import org.jetbrains.kotlinx.jupyter.plugin.editor.find.isIdentifier
import org.jetbrains.kotlinx.jupyter.plugin.editor.notifications.NotebookNotificationUtility
import org.jetbrains.kotlinx.jupyter.plugin.editor.refactoring.NotebookRefactoringSupport.isNotebookRefactoringSupported
import org.jetbrains.kotlinx.jupyter.plugin.editor.refactoring.NotebookRefactoringSupport.tryCastParentToSuitableTarget
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.NotebookStructureTrackerService
import org.jetbrains.kotlinx.jupyter.plugin.util.isInsideKotlinNotebookFile
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.util.retrieveElementUnderCaret
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterNotebook
import org.jetbrains.plugins.notebooks.jupyter.psi.impl.JupyterPsiCellImpl
import java.awt.Component

internal object NotebookRefactoringSupport {
    private val refactorSupportedClasses = setOf(
        KtProperty::class.java,
        KtFunction::class.java,
        KtNamedFunction::class.java,
        KtClass::class.java,
        KtPrimaryConstructor::class.java
    )

    fun isNotebookRefactoringSupported(element: PsiElement?): Boolean = element != null && element::class.java in refactorSupportedClasses

    fun tryCastParentToSuitableTarget(element: PsiElement?): PsiElement? {
        val parentClass = element?.parent?.javaClass ?: return null
        refactorSupportedClasses.forEach {
            if (parentClass == it) return it.cast(element.parent)
        }
        return null
    }
}

// MemberInplaceRenameHandler
class NotebookPropertyRenameProcessor : RenamePsiElementProcessor() {
    private val goToDeclarationProvider = NotebookGotoDeclarationProvider()

    private fun tryResolveToDeclaration(element: PsiElement, editor: Editor?): PsiElement? {
        goToDeclarationProvider.getGotoDeclarationTargets(element, editor?.caretModel?.offset ?: 0 , editor)?.firstOrNull()?.let {
            return it
        } ?: return null
    }

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {

    }

    override fun canProcessElement(element: PsiElement): Boolean {
        val containingFile = element.containingFile ?: return false
        val vFile = containingFile.virtualFile ?: return false
        if (!vFile.isKotlinNotebook && !isKotlinNotebookInjectedFile(containingFile)) return false
        val namedUnwrappedElement = element.namedUnwrappedElement
        return (namedUnwrappedElement is KtProperty || namedUnwrappedElement is RenameKotlinPropertyProcessor.PropertyMethodWrapper
                || (element is LeafPsiElement && namedUnwrappedElement is KtScript))
                || (namedUnwrappedElement != null && namedUnwrappedElement is KtParameter && namedUnwrappedElement.hasValOrVar())
    }


    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean
    ): Collection<PsiReference> {
        return KotlinNotebookElementFindUsagesHandler(element).findReferencesToHighlight(element, searchScope)
    }

    override fun isInplaceRenameSupported(): Boolean {
        return true
    }

    override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement {
        if (!isKotlinNotebookInjectedFile(element.containingFile)) return element
        return tryResolveToDeclaration(element, editor) ?: element
    }

    override fun substituteElementToRename(element: PsiElement, editor: Editor, renameCallback: Pass<in PsiElement>) {
        if (!isKotlinNotebookInjectedFile(element.containingFile)) return
        val adjustedElement = tryResolveToDeclaration(element, editor) ?: element.parent.reference?.resolve()
        if ((adjustedElement == null && !isNotebookRefactoringSupported(element.parent)) || adjustedElement?.containingFile is KtClsFile) {
            NotebookNotificationUtility.getInstance(element.project)
                .usageRelatedFactory.showBytecodeRefactoringWarning(editor.project)
        } else {
            val parent = tryCastParentToSuitableTarget(element)
            val properElem = adjustedElement ?: parent ?: return
            renameCallback.accept(properElem)
        }
    }

}

class KotlinNotebookPropertiesRenameHandler : MemberInplaceRenameHandler() {
    internal companion object {
        private val log = thisLogger()
    }
    private fun findNearestActualElementAt(file: PsiFile, caretModel: CaretModel): PsiElement? {
        var shift = 0
        var element: PsiElement?
        do {
            element = file.findElementAt(caretModel.offset - shift++)
        } while (!element.isIdentifier() && element != null)
        return element
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile, dataContext: DataContext) {
        var element = findNearestActualElementAt(file, editor!!.caretModel)
        if (element == null) {
            if (LookupManager.getActiveLookup(editor) != null) {
                val elementUnderCaret = file.findElementAt(editor.caretModel.offset)
                if (elementUnderCaret != null) {
                    val parent = elementUnderCaret.parent
                    element = if (parent is PsiReference) {
                        (parent as PsiReference).resolve()
                    } else {
                        PsiTreeUtil.getParentOfType(elementUnderCaret, PsiNamedElement::class.java)
                    }
                }
                if (element == null) return
            } else {
                return
            }
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        if (checkAvailable(element, editor, dataContext)) {
            doRename(element, editor, dataContext)
        }
    }

    private fun createDataContext(contextComponent: Component?, newName: String?, newElementToRename: PsiElement?): DataContext {
        val context = DataManager.getInstance().getDataContext(contextComponent)
        return if (newName == null && newElementToRename == null) context else SimpleDataContext.builder()
            .setParent(context)
            .add(PsiElementRenameHandler.DEFAULT_NAME, newName)
            .add(LangDataKeys.PSI_ELEMENT_ARRAY, newElementToRename?.let { arrayOf(it) })
            .build()
    }

    // before actual doRename
    override fun checkAvailable(elementToRename: PsiElement, editor: Editor?, dataContext: DataContext): Boolean {
        val psiFile = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return false
        return isKotlinNotebookInjectedFile(psiFile) || psiFile.isInsideKotlinNotebookFile() //&& elementToRename is KtProperty
    }

    override fun isRenaming(dataContext: DataContext): Boolean {
        val psiFile = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return false
        val virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext) ?: return false
        val storedPsiElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext)
        val psiElement = storedPsiElement ?:
            retrieveElementUnderCaret(psiFile) ?: return false

        val resolvedElement = when (val parent = psiElement.parent) {
            is KtReferenceExpression -> parent.reference?.resolve()
            else -> psiElement
        } ?: return false

        val containingFile = resolvedElement.containingFile
        val isCompiledElem = containingFile is KtClsFile
        val manager = InjectedLanguageManager.getInstance(psiFile.project)
        if (isCompiledElem) {
            if (!containingFile.name.startsWith("Line_")) return false
        }

        val cell = (manager.getInjectionHost(psiElement.containingFile) as? JupyterPsiCellImpl)
        val ind = (cell?.parent as? JupyterNotebook)?.psiCellList?.indexOf(cell) // todo: might be costy
        val notebookFile = if (virtualFile is VirtualFileWindow) virtualFile.delegate else virtualFile

        return psiFile.isInsideKotlinNotebookFile()
                && isNotebookRefactoringSupported(resolvedElement)
                && (isCompiledElem
                || cell?.getUserData(CELL_CLASS_NAME) != null
                || NotebookStructureTrackerService.getForFile(psiFile.project, BackedNotebookVirtualFile(notebookFile))
                    .cellOrdinalToClassNameStructure[ind] != null)
    }

    override fun doRename(elementToRename: PsiElement, editor: Editor, dataContext: DataContext?): InplaceRefactoring? {
        val contextComponent = runIf(dataContext != null) {
            PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext!!)
        } ?: editor.component
        val newName = if (dataContext != null) PsiElementRenameHandler.DEFAULT_NAME.getData(dataContext) else null
        var newElementToRename: PsiElement? = null
        if (elementToRename is PsiNameIdentifierOwner || elementToRename is LeafPsiElement) {
            val processor = NotebookPropertyRenameProcessor()
            if (processor.isInplaceRenameSupported) {
                val startMarkAction = StartMarkAction.canStart(editor)
                if (startMarkAction == null || processor.substituteElementToRename(elementToRename, editor)
                        .also { newElementToRename = it } === elementToRename
                ) {
                    processor.substituteElementToRename(elementToRename, editor, object : Pass<PsiElement>() {
                        override fun pass(element: PsiElement) {
                            val identifier = element as PsiNameIdentifierOwner
                            if (elementToRename is LeafPsiElement && identifier.name != elementToRename.text) {
                                log.debug("Renaming aborted: found element to rename ${elementToRename} differs from ${identifier.name}")
                                return
                            }
                            val renamer = createMemberRenamer(
                                elementToRename,
                                identifier, editor
                            )
                            val startedRename = renamer.performInplaceRename()
                            if (!startedRename) {
                                performDialogRename(
                                    element,
                                    editor,
                                    createDataContext(contextComponent, newName, element),
                                    renamer.initialName
                                )
                            }
                        }
                    })
                    return null
                } else {
                    val inplaceRefactoring = editor.getUserData(InplaceRefactoring.INPLACE_RENAMER)
                    if (inplaceRefactoring != null && inplaceRefactoring.javaClass == MemberInplaceRenamer::class.java) {
                        val templateState = TemplateManagerImpl.getTemplateState(InjectedLanguageEditorUtil.getTopLevelEditor(editor))
                        templateState?.gotoEnd(true)
                    }
                }
            }
        }
        performDialogRename(elementToRename, editor, createDataContext(contextComponent, newName, newElementToRename), null)
        return null
    }


    override fun createMemberRenamer(element: PsiElement, elementToRename: PsiNameIdentifierOwner, editor: Editor): MemberInplaceRenamer {
        val originalCaretElement = InjectedLanguageManager.getInstance(element.project).getInjectionHost(element)
        return NotebookMemberInplaceRenamer(element, elementToRename, editor, originalCaretElement)
    }

    override fun isAvailable(element: PsiElement?, editor: Editor, file: PsiFile): Boolean {
        return file.isInsideKotlinNotebookFile() &&
                (element == null || element.containingFile is KtFile && isNotebookRefactoringSupported(element))
    }
}

fun isKotlinNotebookInjectedFile(file: PsiFile?): Boolean {
    if (file == null) return false
    if (!file.isScript()) return false

    val service = JupyterCompilerService.getInstance(file.project)
    return file.name.endsWith(service.fileSuffix)
}

internal fun PsiReference.toMoveUsageInfo() =
    MoveRenameUsageInfo(element, this, rangeInElement.startOffset, rangeInElement.endOffset, resolve(), false)