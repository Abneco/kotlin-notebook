// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.util

import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.util.getNotebookCells
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterFile
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell

/**
 * Prefix and suffix of the names of classes to which snippets are compiled,
 * as well as the corresponding classes names on the IDE side
 */
const val NOTEBOOK_COMPILED_CLASS_NAME_PREFIX: String = "Line_"
const val NOTEBOOK_COMPILED_CLASS_NAME_SUFFIX: String = "_jupyter"

/**
 * NB: this class is not thread-safe
 *
 * todo: to be removed as its logic is rather complicated and error-prone on structure changes
 */
class ExecutedPresentCellInfo(psiFile: JupyterFile?) {
    private val knownCellClasses = mutableMapOf<String, JupyterPsiCell>()
    val cellOrdinalToClassName: MutableMap<Int, Set<String>> = mutableMapOf()
    val classNameToCellOrdinal: MutableMap<String, Int> = mutableMapOf()
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
        val compiledName = nextCompiledClassNumber.toCompiledCellSnippetName()
        if (was != null && was != nextCompiledClassNumber.toCompiledCellSnippetName()) {
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
        private val NOTEBOOK_CELL_INTERNAL_INFO_KEY = Key.create<Pair<String, String?>>("NOTEBOOK_CELL_INTERNAL_INFO_KEY")

        // returns previous known class if any
        fun updateCellInjectedInfo(cell: JupyterPsiCell, execNum: Int): String? {
            val was = cell.getUserData(NOTEBOOK_CELL_INTERNAL_INFO_KEY)
            val snippetName = execNum.toCompiledCellSnippetName() // Line_$N_jupyter
            if (was?.first == snippetName) return was.first
            cell.putUserData(NOTEBOOK_CELL_INTERNAL_INFO_KEY,  Pair(snippetName, was?.first))
            LOG.debug("Putting into cell: \n ${cell.text}\n info: ${execNum.toCompiledCellSnippetName()}; was: $was")
            return was?.first
        }
    }
}

fun Int.toCompiledCellSnippetName(): String = "$NOTEBOOK_COMPILED_CLASS_NAME_PREFIX${this}$NOTEBOOK_COMPILED_CLASS_NAME_SUFFIX"
