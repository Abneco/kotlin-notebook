// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.nbformat.JupyterNotebook
import com.intellij.kotlin.jupyter.core.debug.util.ExecutedPresentCellInfo
import com.intellij.kotlin.jupyter.core.editor.codeInsight.NotebookGotoDeclarationProvider.Companion.clearResolvedUsagesCaches
import com.intellij.kotlin.jupyter.core.editor.codeInsight.findAllDeclarationsOfType
import com.intellij.kotlin.jupyter.core.jupyter.cells.ExecutedCellData
import com.intellij.kotlin.jupyter.core.jupyter.cells.NotebookExecutionRelatedMetaData
import com.intellij.kotlin.jupyter.core.jupyter.cells.NotebookExecutionRelatedMetaData.Companion.storeExecutionRelatedMetaData
import com.intellij.kotlin.jupyter.core.jupyter.cells.clearAllCellsDataByKey
import com.intellij.kotlin.jupyter.core.jupyter.cells.executionMetadata
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.util.NotebookPerFileChildService
import com.intellij.kotlin.jupyter.core.util.findPsiFile
import com.intellij.kotlin.jupyter.core.util.getInjectedKtFiles
import com.intellij.kotlin.jupyter.core.util.getNotebookCells
import com.intellij.kotlin.jupyter.core.util.withReadAccess
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.util.getValueOrNull
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlinx.jupyter.repl.EvaluatedSnippetMetadata
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterFile
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell

/**
 * Keeps information about what classes were compiled in the current Notebook.
 * Information is provided in accordance with the cell index in the Notebook structure.
 */
internal interface NotebookClassesInCellsInfoHandler {
    val nextCompiledClassLineIndex: Int

    val cellOrdinalToCompiledClassNames: Map<Int, Set<String>>
    val classNameToCellOrdinalStructure: MutableMap<String, Int>

    fun storeCompliedDataInCell(snippetMetadata: EvaluatedSnippetMetadata, executedCellData: ExecutedCellData)

    fun findPsiCellByClassName(className: String): JupyterPsiCell?

    fun findPsiDeclarationsInsideCompliedCellByName(className: String, elementName: String): Collection<PsiElement>?

    fun clearData()
}


class NotebookStructurePerFileTracker(
    private val project: Project,
    virtualFile: BackedNotebookVirtualFile,
    scope: CoroutineScope,
): NotebookPerFileChildService(virtualFile, scope), NotebookClassesInCellsInfoHandler {
    private val psiFile by lazy {
        withReadAccess {
            virtualFile.file.findPsiFile(project) as JupyterFile
        }
    }
    private val notebook: JupyterNotebook
        get() = virtualFile.notebook
    private val injectedManager: InjectedLanguageManager
        get() = InjectedLanguageManager.getInstance(project)

    private val knownCellInfoDelegate = lazy {
        ExecutedPresentCellInfo(psiFile)
    }
    private val knownCellInfo by knownCellInfoDelegate

    override val cellOrdinalToCompiledClassNames: Map<Int, Set<String>>
        get() = buildMap {
            for ((index, cell) in notebook.computeCells().withIndex()) {
                val compiledClassSet = cell.executionMetadata?.compiledClasses?.toSet().orEmpty()
                put(index, compiledClassSet)
            }
        }

    override val classNameToCellOrdinalStructure: MutableMap<String, Int>
        get() = knownCellInfo.classNameToCellOrdinal

    override val nextCompiledClassLineIndex: Int
        get() {
            val cellsCounter = JupyterCompilerService.getForFile(project, virtualFile).executedCellsCount - 1
            return  if (cellsCounter == -1) 1 else cellsCounter + 1
        }

    /**
     * Search is performed by relying on the stored meta-data about compiled classes.
     * @see [com.intellij.kotlin.jupyter.core.jupyter.cells.NotebookExecutionRelatedMetaData]
     */
    override fun findPsiCellByClassName(className: String): JupyterPsiCell? {
        val (ktFiles, jupyterCells) = runReadAction {
            psiFile.getInjectedKtFiles() to virtualFile.notebook.computeCells()
        }
        if (ktFiles.isEmpty()) {
            LOG.warn("No injected KtFiles present inside ${virtualFile.file.name}")
            return null
        }

        val psiCells = psiFile.getNotebookCells()
        for ((index, cell) in jupyterCells.withIndex()) {
            val cellExecutionRelatedData = cell.executionMetadata ?: continue
            val compiledClasses = cellExecutionRelatedData.compiledClasses
            if (className in compiledClasses) {
                return psiCells.getOrNull(index)
            }
        }

        return null
    }

    override fun findPsiDeclarationsInsideCompliedCellByName(className: String, elementName: String): Collection<PsiElement>? {
        val psiCell = findPsiCellByClassName(className) ?: return null
        val ktFiles = psiCell.getInjectedKtFiles(injectedManager)
        val matchedDeclarations = mutableSetOf<KtDeclaration>()

        for (file in ktFiles) {
            val declarations = file.findAllDeclarationsOfType<KtNamedDeclaration>()

            for (declaration in declarations) {
                if (elementName in declaration.nameAsName?.identifier.orEmpty()) {
                    matchedDeclarations.add(declaration)
                }
            }
        }

        return null
    }

    override fun storeCompliedDataInCell(snippetMetadata: EvaluatedSnippetMetadata, executedCellData: ExecutedCellData) {
        fun JupyterPsiCell?.storeReferenceInfo(compiledClassName: MutableSet<String>) {
            if (this == null) return
            synchronized(this) {
                val last = getUserData(CELL_CLASS_NAME)?.firstOrNull()
                compiledClassName.addIfNotNull(last)
                putUserData(CELL_CLASS_NAME, compiledClassName)
            }
        }
        val classNamesToCellOrdinal = classNameToCellOrdinalStructure

        val compiledClassNames = snippetMetadata.compiledData.sources.mapTo(mutableSetOf()) {
            it.fileName.substringBefore(".kts").let { f -> f + "_jupyter" }
        }
        if (compiledClassNames.isEmpty()) return

        val psiCell = executedCellData.psiCell
        val cellIndex = executedCellData.cellIndex
        val notebookCell = notebook.getCell(cellIndex)
        if (cellIndex != -1) {
            compiledClassNames.forEach { classNamesToCellOrdinal[it] = cellIndex }
        }
        psiCell.storeReferenceInfo(compiledClassNames)

        try {
            notebookCell.storeExecutionRelatedMetaData(compiledClassNames)
        } catch (ex: Exception) {
            LOG.warn("Exception during storing cell-related data", ex)
        }
    }

    fun updateCellInformationBeforeExecution(cell: JupyterPsiCell, ordinal: Int?) {
        knownCellInfo.updateInfoBeforeCellExecution(
            cell,
            ordinal,
            nextCompiledClassLineIndex
        )
    }

    /**
     * Clears temporary data stored in the PSI level
     * used in [com.intellij.kotlin.jupyter.core.editor.codeInsight.NotebookGotoDeclarationProvider]
     */
    fun clearPsiLevelReferencesData() {
        // maybe we don't need caching at all?
        for (file in psiFile.getInjectedKtFiles()) {
            file.clearResolvedUsagesCaches()
        }
    }

    override fun clearData() {
        knownCellInfoDelegate.getValueOrNull()?.clear()
        notebook.clearAllCellsDataByKey(NotebookExecutionRelatedMetaData.DATA_KEY)
    }

    override fun dispose() {
        clearData()
    }

    companion object {
        // Holds compiled class name
        val CELL_CLASS_NAME: Key<Set<String>> = Key.create("COMPILED_CELL_SCRIPT_CLASS_NAME")

        private val LOG = notebookLogger()
    }
}