// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.InjectedLanguageHighlightingRangeReducer
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.injected.changesHandler.range
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.jetbrains.kotlin.idea.base.highlighting.visitor.AbstractAnnotationHolderHighlightingVisitor
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlinx.jupyter.plugin.actions.refactor.NotebookNotificationUtility.showKernelRestart
import org.jetbrains.kotlinx.jupyter.plugin.editor.AbstractKotlinHighlightingVisitorAdapter
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.InjectedHostHasErrors
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.NOTEBOOK_DOCUMENT_TARGET_ANALYSIS_RANGE
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.NonTargetHostErrorRegistry
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.NotebookDocumentTargetRanges
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.RenamingEnclosedRange
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.notebookInjectedFileExtension
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.scheduleUpdateLater
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.NotebookReferenceFinder
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterFile
import org.jetbrains.plugins.notebooks.visualization.getCell
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min


internal class KotlinNotebookInjectedRangeReducer : InjectedLanguageHighlightingRangeReducer {
    private val notebookCodeUtility = NotebookHighlightingUtilityObject
    private val dummyTextChangeRange = TextRange(0, 0)

    override fun reduceRange(file: PsiFile, editor: Editor): Collection<TextRange>? {
        if (!notebookCodeUtility.isLooksLikeNotebookFile(file)) return null

        val jupyterFile = file as? JupyterFile ?: return null
        val document = FileDocumentManager.getInstance().getDocument(jupyterFile.virtualFile) ?: return null

        val cells = file.getNotebookCellList()
        cells?.ensureScriptConfigurations(ScriptConfigurationManager.getInstance(file.project),
                                                               InjectedLanguageManager.getInstance(file.project))
        jupyterFile.ensureScriptManagerReady()


        return synchronized(document) {
            val afterRenaming = document.getUserData(RenamingEnclosedRange)
            if (afterRenaming?.isNotEmpty() == true) {
                return afterRenaming
            }
            val severalUpdates = document.getUserData(NotebookDocumentTargetRanges)
            if (severalUpdates?.isNotEmpty() == true) {
                return severalUpdates
            }

            val cellInd = document.getUserData(NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX)?.let { // notebook file is already rebuild
                cells?.get(it)
            }?.textRange
            val possibleRange = document.getUserData(NOTEBOOK_DOCUMENT_TARGET_ANALYSIS_RANGE)
            //if (cellInd != null && possibleRange?.endOffset != cellInd.endOffset) cellInd else possibleRange
            possibleRange
        }?.let {
            listOf(TextRange(it.startOffset, it.endOffset + 1))
        }
    }

    private fun Collection<PsiLanguageInjectionHost>?.ensureScriptConfigurations(scriptingManager: ScriptConfigurationManager, manager: InjectedLanguageManager) {
        this?.forEach {
            manager.getInjectedPsiFiles(it)?.firstOrNull { f -> f.first is KtFile }?.first?.let { ktFile ->
                if (ktFile is KtFile) {
                    scriptingManager.getConfiguration(ktFile)
                }
            }
        }
    }

    private fun PsiFile.ensureScriptManagerReady() {
        val scriptDefManager = ScriptDefinitionsManager.getInstance(project)
        if (scriptDefManager.isReady()) return

        if (ApplicationManager.getApplication().isDispatchThread) {
            scheduleUpdateLater(this)
        } else throw ProcessCanceledException()
        // for some reason, in debug mode calling isReady() might cause DL
    }
}

class InjectedFileHighlightingHelper(val injectedFile: PsiFile, isFirstPass: Boolean) {
    private val project = injectedFile.project
    private lateinit var targetHost: PsiLanguageInjectionHost
    private var errorRegistry: Collection<HighlightInfo>? = null
    private val injectedManager = InjectedLanguageManager.getInstance(project)
    private var completeAnalysisRange: TextRange? = null
    init {
        assert(tryUpdateCurrentInjectedFileTarget(isFirstPass))
    }
    private var isShouldHighlightErrors: Boolean = false

    private fun tryUpdateCurrentInjectedFileTarget(completeUpdate: Boolean): Boolean {
        targetHost = injectedManager.getInjectionHost(injectedFile) ?: return false
        if (!completeUpdate) {
            errorRegistry = synchronized(injectedFile) {
                injectedFile.getUserData(NonTargetHostErrorRegistry)
            }
            isShouldHighlightErrors = errorRegistry == null
            return true
        }
        completeAnalysisRange = NotebookHighlightingUtilityObject.getCompleteAnalysisRangeForWholeNotebook(injectedFile)
        isShouldHighlightErrors = completeAnalysisRange?.contains(targetHost.textRange) ?:
                (completeAnalysisRange != null && isEitherSymmetricallyContainedRange(completeAnalysisRange!!, targetHost.textRange.shiftLeft(1)))
        //if (isShouldHighlightErrors) {
        //    println("Should highlight errors for ${injectedFile.name} with range: ${targetHost?.range}")
        //} else println("should not for ${injectedFile.name} with range: ${targetHost?.range}")


        return true
    }

    fun markTargetHost() {
        injectedFile.putUserData(NonTargetHostErrorRegistry, if (isShouldHighlightErrors) null else mutableSetOf())
    }

    fun updateHolderOrProvided(holder: HighlightInfoHolder) {
        if (isShouldHighlightErrors) {
            return
        }
        val errorRef = targetHost.getUserData(InjectedHostHasErrors)
            ?: AtomicReference(true).also { targetHost.putUserData(InjectedHostHasErrors, it) }
        val registry = errorRegistry ?: return
        if (registry.isNotEmpty()) {
            errorRef.set(true)
            for (el in registry) {
                if (el.severity == HighlightSeverity.ERROR) {
                    holder.add(HighlightInfoManipulator.convertToShadowedDeclaration(el))
                }
            }
        } else errorRef.compareAndSet(true, false)
    }

}

internal fun isEitherSymmetricallyContainedRange(lhs: TextRange, rhs: TextRange): Boolean = lhs.contains(rhs) || rhs.contains(rhs)

internal object NotebookHighlightingUtilityObject {
    const val notebookInjectedFileExtension: String = "jupyter.kts"
    private const val notebookInjectedMetaFileExtension: String = "juktm"
    private const val notebookDocumentFileExtension: String = "ipynb"
    private val updateScope = CoroutineScope(Dispatchers.Default)

    @JvmField
    val NOTEBOOK_DOCUMENT_TARGET_ANALYSIS_RANGE = Key.create<TextRange>("notebook.document.target.range")
    // to unify
    val NotebookDocumentTargetRanges = Key.create<Collection<TextRange>>("notebook.document.target.ranges")
    @JvmField
    val ANALYZER_PASS_INJECTED_INFO_HOLDER_KEY: Key<HighlightInfoHolder> = Key.create("injected.element.pass.info.holder")

    internal val InjectedHostHasErrors = Key.create<AtomicReference<Boolean>>("injected.element.errors.found")
    internal val RenamingEnclosedRange: Key<Collection<TextRange>> = Key.create("notebook.after.rename.changed.range")
    internal val CompleteHighlightingRange: Key<TextRange> = Key.create("notebook.document.errors.analysis.range")
    internal val NonTargetHostErrorRegistry: Key<MutableCollection<HighlightInfo>> = Key.create("injected.element.actual.errors.registry")

    internal val NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX: Key<Int> = Key.create("notebook.document.target.cell.ind")

    fun scheduleUpdateLater(file: PsiFile, delayDelta: Long = 700) {
        updateScope.async {
            val manager = ScriptDefinitionsManager.getInstance(file.project)
            var isReady = manager.isReady()
            while (!isReady) {
                delay(delayDelta)
                isReady = manager.isReady()
            }
            invokeLater {
                file.restartAnalyzing()
            }
        }
    }

    fun getCompleteAnalysisRangeForWholeNotebook(injectedFile: PsiFile): TextRange? {
        val project = injectedFile.project
        val manager = InjectedLanguageManager.getInstance(project)
        val topLevelFile = manager.getTopLevelFile(injectedFile)
        return topLevelFile.toDocument(project)?.let {
            synchronized(it) {
                it.getUserData(CompleteHighlightingRange)
            }
        }
    }

    fun isLooksLikeNotebookDocument(document: Document): Boolean =
        FileDocumentManager.getInstance().getFile(document)?.extension == notebookDocumentFileExtension

    fun isLooksLikeNotebookFile(file: PsiFile): Boolean =
        file.fileType.defaultExtension == notebookDocumentFileExtension

    fun Document.invalidateStateAfterCellExecution(executedCell: PsiLanguageInjectionHost? = null) {
        putUserData(RenamingEnclosedRange, null)
        putUserData(NotebookDocumentTargetRanges, null)
        putUserData(NOTEBOOK_DOCUMENT_TARGET_ANALYSIS_RANGE, executedCell?.textRange)
        putUserData(CompleteHighlightingRange, executedCell?.textRange)
        putUserData(NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX, null)
    }


    /**
     * [require] ReadAction
     * [require] EDT thread
     */
    fun resetSessionMetaInformation(document: Document, vFile: VirtualFile, project: Project, wouldShowNotification: Boolean = true) {
        val cell = FileEditorManager.getInstance(project).getSelectedEditor(vFile)?.safeAs<TextEditor>()?.let {
            val editor = it.editor
            val pos = editor.caretModel.logicalPosition
            val cell = editor.getCell(min(pos.line, document.lineCount - 1))
            vFile.toPsiFile(project)?.getNotebookCellList()?.get(cell.ordinal)
        }
        document.invalidateStateAfterCellExecution(cell)
        val psiFile = vFile.toPsiFile(project)
        psiFile?.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, null)
        psiFile?.getNotebookCellList()?.forEach {
            it.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, null)
        }
        if (wouldShowNotification) {
            showKernelRestart(project)
        }
        invokeLater {
            psiFile?.restartAnalyzing()
        }
    }
}


internal object HighlightInfoManipulator {
    @NlsSafe
    private const val shadowedSymbolDescription = "Not yet provided symbol"
    private val shadowedSymbolSeverity = HighlightInfo.convertSeverity(HighlightSeverity.INFORMATION)

    fun convertToShadowedDeclaration(info: HighlightInfo): HighlightInfo {
        val n = HighlightInfo.newHighlightInfo(shadowedSymbolSeverity)
            .range(info.range)
            .description(shadowedSymbolDescription)
            .textAttributes(CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES)
            .unescapedToolTip(shadowedSymbolDescription)
            .needsUpdateOnTyping(info.needUpdateOnTyping())
            .group(0)

        return if (info.isAfterEndOfLine)
                    n.endOfLine().createUnconditionally()
                else n.createUnconditionally()
    }
}

internal class KotlinNotebookBeforeHighlightingVisitor: AbstractKotlinHighlightingVisitorAdapter<KotlinNotebookDummyVisitor> (
    { annotationHolder -> KotlinNotebookDummyVisitor(annotationHolder) }
) {
    override fun clone(): HighlightVisitor {
        return KotlinNotebookBeforeHighlightingVisitor()
    }

    override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        prepareForFileAndAdjust(file, holder, stage = PassStage.MarkTargetHostBeforeHighlighting)
        return true
    }
}

internal class KotlinNotebookDummyVisitor(holder: AnnotationHolder) : AbstractAnnotationHolderHighlightingVisitor(holder) {
    override fun visitFile(file: PsiFile) {
        return
    }
}


class KotlinNotebookHighlightingErrorFilter: HighlightInfoFilter {
    override fun accept(highlightInfo: HighlightInfo, file: PsiFile?): Boolean {
        if (file == null || !file.name.endsWith(notebookInjectedFileExtension)) return true
        val errorRegistry = file.getUserData(NonTargetHostErrorRegistry) ?: return true

        if (highlightInfo.severity == HighlightSeverity.ERROR) {
            errorRegistry.add(highlightInfo)
            return false
        }

        return true
    }
}