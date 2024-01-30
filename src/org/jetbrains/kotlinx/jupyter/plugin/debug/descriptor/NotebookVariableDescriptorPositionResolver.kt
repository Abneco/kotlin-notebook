// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.ui.tree.FieldDescriptor
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.childrenOfType
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor.api.DeclaredVariableSourcePositionProvider
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.NotebookStructureTrackerService
import org.jetbrains.kotlinx.jupyter.plugin.util.getInjectedKtFiles
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.util.toBackedNotebookFile

object NotebookVariableDescriptorPositionResolver : DeclaredVariableSourcePositionProvider {
    override fun resolveTo(project: Project, virtualFile: VirtualFile?, descriptor: FieldDescriptor): SourcePosition? {
        if (!virtualFile.isKotlinNotebook) return null
        val backedNotebookVirtualFile = virtualFile?.toBackedNotebookFile() ?: return null

        val containingClass = descriptor.`object`.referenceType().name()
        val psiCell = NotebookStructureTrackerService.getForFile(project, backedNotebookVirtualFile)
            .findPsiCellByClassName(containingClass) ?: return null

        val ktFile = psiCell.getInjectedKtFiles(InjectedLanguageManager.getInstance(project)).firstOrNull() ?: return null
        val knownTargetDeclaration = ktFile.childrenOfType<KtScript>().firstOrNull()
            ?.declarations?.firstOrNull {
                it is KtProperty && it.name == descriptor.name
            } as? KtProperty ?: return null

        return SourcePosition.createFromElement(
            knownTargetDeclaration.nameIdentifier ?:
            knownTargetDeclaration.navigationElement
        )
    }
}