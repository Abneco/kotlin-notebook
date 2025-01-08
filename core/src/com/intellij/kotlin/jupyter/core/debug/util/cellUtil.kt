// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.util

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.scriptingSupport.NotebookStructureTrackerService
import com.intellij.kotlin.jupyter.core.util.getNotebookCells
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell


class ExecutedPresentCellInfo(psiFile: PsiFile?) {
    private val knownCellClasses = mutableMapOf<String, JupyterPsiCell>()
    val cellOrdinalToClassName = mutableMapOf<Int, Set<String>>()
    val classNameToCellOrdinal = mutableMapOf<String, Int>()
    var cells: List<JupyterPsiCell>? = null

    var jupyterFile: PsiFile? = psiFile
        set(value) {
            field = value
            if (value != null) {
                updateCellsByFile(value)
            } else cells = null

            knownCellClasses.clear()
        }

    fun updateInfoBeforeCellExecution(cell: JupyterPsiCell, ordinal: Int?, nextCompiledClassNumber: Int) {
        val was = updateCellInjectedInfo(cell, nextCompiledClassNumber)
        val cellOrdinal = ordinal ?: cells?.indexOf(cell)
        val compiledName = nextCompiledClassNumber.toCompiledCellSnippedName()
        if (was != null && was != nextCompiledClassNumber.toCompiledCellSnippedName()) {
            knownCellClasses.remove(was)
        }
        knownCellClasses[compiledName] = cell
        if (cellOrdinal != null) {
            classNameToCellOrdinal[compiledName] = cellOrdinal
        }
    }

    fun clear() {
        cellOrdinalToClassName.clear()
        classNameToCellOrdinal.clear()
        knownCellClasses.clear()
    }

    fun structureChanged() {
        // update state
        classNameToCellOrdinal.clear()
        knownCellClasses.clear()
        cellOrdinalToClassName.forEach { (ind, classes) ->
            classes.forEach {
                classNameToCellOrdinal[it] = ind
                cells?.getOrNull(ind)?.let { psiCell -> knownCellClasses[it] = psiCell }
            }
        }
    }

    fun correspondingCellToClass(cellClassName: String): JupyterPsiCell? {
        val properName = if (cellClassName.matches(Regex(".+\\..+"))) cellClassName.split(Regex("\\.")).let {
            if (it[1] == "jupyter") "${it[0]}_${it[1]}" else it.first()
        } else cellClassName
        return classNameToCellOrdinal[properName]?.let {
            val file = jupyterFile
            if (cells == null && file != null) updateCellsByFile(file)
            cells?.getOrNull(it)
        } ?: knownCellClasses[properName]
    }


    private fun updateCellsByFile(file: PsiFile) {
        cells = runReadAction { file.getNotebookCells() }
    }

    companion object {
        private val LOG = notebookLogger()
        // curr, prev
        val NOTEBOOK_CELL_INTERNAL_INFO_KEY = Key.create<Pair<String, String?>>("NOTEBOOK_CELL_INTERNAL_INFO_KEY")

        // returns previous known class if any
        fun updateCellInjectedInfo(cell: JupyterPsiCell, execNum: Int): String? {
            val was = cell.getUserData(NOTEBOOK_CELL_INTERNAL_INFO_KEY)
            val snippedName = execNum.toCompiledCellSnippedName() // Line_$N_jupyter
            if (was?.first == snippedName) return was.first
            cell.putUserData(NOTEBOOK_CELL_INTERNAL_INFO_KEY,  Pair(snippedName, was?.first))
            LOG.debug("Putting into cell: \n ${cell.text}\n info: Line_${execNum}_jupyter; was: $was")
            return was?.first
        }
    }
}

fun Int.toCompiledCellSnippedName(): String = "Line_${this}_jupyter"

fun JupyterPsiCell.updateInfoBeforeExecution(project: Project, virtualFile: BackedNotebookVirtualFile, cellOrdinal: Int?) {
    runReadAction {
        NotebookStructureTrackerService.getForFile(project, virtualFile)
            .updateCellInformationBeforeExecution(this@updateInfoBeforeExecution, cellOrdinal)
    }
}
