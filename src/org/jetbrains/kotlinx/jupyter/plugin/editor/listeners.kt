// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlinx.jupyter.plugin.file.toPsiFile
import org.jetbrains.plugins.notebooks.core.impl.file.isBackedNotebook
import org.jetbrains.plugins.notebooks.visualization.getCell
import kotlin.math.min

class NotebookCaretListener(private val project: Project, private val vFile: VirtualFile,
                            private val editor: Editor): CaretListener {
    private val psiFile = vFile.toPsiFile(project)
    private var lastCellInd: Int = -1
    private var lastTimeCellFocusChanged = 0L
    init {
        assert(isBackedNotebook(vFile))
        assert(psiFile != null)
    }

    override fun caretPositionChanged(event: CaretEvent) {
        if (lastCellInd == -1) {
            lastCellInd = 0
            lastTimeCellFocusChanged = System.currentTimeMillis()
            return
        }
        val cell = editor.getCell(min(event.newPosition.line, editor.document.lineCount - 1))
        val ord = cell.ordinal
        if (ord == lastCellInd) return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTimeCellFocusChanged < 600) {
            //println("should not trigger an event! for cell $ord")
        } else {
            lastCellInd = ord
            //println("Cell focus changed to $ord")
        }
        lastTimeCellFocusChanged = System.currentTimeMillis()
        super.caretPositionChanged(event)
    }
}

