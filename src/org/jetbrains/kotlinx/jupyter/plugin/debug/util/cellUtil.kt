// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.util

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.NotebookStructureTrackerService
import org.jetbrains.kotlinx.jupyter.plugin.util.getNotebookValidCells
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell


class ExecutedPresentCellInfo(psiFile: PsiFile?) {
    private val knownCellClasses = mutableMapOf<String, JupyterPsiCell>()
    val cellOrdinalToClassName = mutableMapOf<Int, Set<String>>()
    val classNameToCellOrdinal = mutableMapOf<String, Int>()
    var cells: List<JupyterPsiCell>? = null

    var jupyterFile: PsiFile? = psiFile
        set(value) {
            field = value
            cells = runReadAction { value.getNotebookValidCells() }

            knownCellClasses.clear()
        }

    fun updateInfoBeforeCellExecution(cell: JupyterPsiCell, ordinal: Int?, nextCompiledClassNumber: Int) {
        val was = updateCellInjectedInfo(cell, nextCompiledClassNumber)
        val cellOrdinal = ordinal ?: cells?.indexOf(cell)
        val compiledName = nextCompiledClassNumber.toCompiledCellSnippedName()
        if (was != null && was != nextCompiledClassNumber.toCompiledCellSnippedName()) {
            //LOG.warn("exec callback: $was != Line_${finalNum}")
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
                cells?.get(ind)?.let { psiCell -> knownCellClasses[it] = psiCell }
            }
        }
    }

    fun ensureForTheScript(cellClassName: String, cell: JupyterPsiCell) {
        if (knownCellClasses.containsKey(cellClassName)) return
        knownCellClasses[cellClassName] = cell

        if (cell.isValid && !classNameToCellOrdinal.containsKey(cellClassName)) {
            cells?.indexOf(cell)?.let { classNameToCellOrdinal[cellClassName] = it }
        }
    }

    fun correspondingCellToClass(cellClassName: String): JupyterPsiCell? {
        val properName = if (cellClassName.matches(Regex(".+\\..+"))) cellClassName.split(Regex("\\.")).let {
            if (it[1] == "jupyter") "${it[0]}_${it[1]}" else it.first()
        } else cellClassName
        return classNameToCellOrdinal[properName]?.let { cells?.get(it) } ?: knownCellClasses[properName]
    }

    companion object {
        private val LOG = thisLogger()
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

fun JupyterPsiCell.ensureValidStateOnErrors(project: Project, virtualFile: BackedNotebookVirtualFile) {
    runReadAction {
        JupyterCompilerService.getForFile(project, virtualFile).also {
            val was = this.getUserData(ExecutedPresentCellInfo.NOTEBOOK_CELL_INTERNAL_INFO_KEY) ?: return@runReadAction
            //println("Rolling back from: ${was} to: ${Pair(was.second ?: "", null)}")
            putUserData(ExecutedPresentCellInfo.NOTEBOOK_CELL_INTERNAL_INFO_KEY, Pair(was.second ?: "", null))
        }
    }
}