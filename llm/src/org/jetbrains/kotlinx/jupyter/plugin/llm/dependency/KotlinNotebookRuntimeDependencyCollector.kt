package org.jetbrains.kotlinx.jupyter.plugin.llm.dependency

import com.intellij.ml.llm.core.chat.context.ChatAttachment
import com.intellij.ml.llm.core.chat.context.traverser.ChatAttachmentWithSourcePosition
import com.intellij.ml.llm.core.chat.context.traverser.CodeDependencyCollector
import com.intellij.model.Pointer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.psi.PsiElement
import org.jetbrains.kotlinx.jupyter.plugin.debug.variables.NotebookSessionVariablesService
import org.jetbrains.kotlinx.jupyter.plugin.llm.chat.functions.NotebookSessionAnalyzerFunction
import org.jetbrains.kotlinx.jupyter.plugin.llm.chat.functions.NotebookVariableExplainerFunction
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.common.toBackedKotlinNotebookOrNull
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.data.VariableDescriberFunctionArguments
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook

internal object NullablePointer : Pointer<PsiElement> {
    override fun dereference(): PsiElement? {
        return null
    }
}

internal val emptySessionAttachment = ChatAttachmentWithSourcePosition(
    NullablePointer,
    ChatAttachment(
        "Kotlin Notebook Session info",
        "No current data is available for the Notebook",
        ChatAttachment.Kind.Visible
    ),
    1
)

internal val refactoringsAttachmentInfo = ChatAttachmentWithSourcePosition(
    NullablePointer,
    ChatAttachment(
        "Declarations inside Kotlin Notebook Session",
        "Please be aware that to make the current declaration visible among other cells\n, " +
                "you need to execute the cell with added declaration",
        ChatAttachment.Kind.Visible
    ),
    1
)


class KotlinNotebookRuntimeDependencyCollector : CodeDependencyCollector {
    companion object {
        private val LOG = thisLogger()
    }

    override suspend fun collectDependencies(chatAttachmentWithSourcePosition: ChatAttachmentWithSourcePosition): Collection<ChatAttachmentWithSourcePosition> {
        LOG.warn("Collecting dependencies for KTNotebook! ChatAttachmentWithSourcePosition: $chatAttachmentWithSourcePosition")
        val pointer = chatAttachmentWithSourcePosition.psiElementPointer

        val (psiElement, vFile) = readAction {
            val psiElem = pointer.dereference()
            psiElem to psiElem?.containingFile?.virtualFile?.toBackedKotlinNotebookOrNull()
        }
        if (psiElement == null || vFile == null || !vFile.file.isKotlinNotebook) return emptyList()

        val varsService = NotebookSessionVariablesService.getForFile(psiElement.project, vFile)
        val variables =
            varsService.getXValueChildrenList()

        if (variables == null || variables.size() == 0) {
            return listOf(
                emptySessionAttachment
            )
        }

        val variableText = readAction { psiElement.text }
/*        val filteredVariables = (0 until variables.size())
            .filter { variables.getName(it) == psiElement.text }
            .map { variables.getValue(it) }*/

        val variableAdditionalInfo = NotebookVariableExplainerFunction
            .invokeWithParameters(
                psiElement.project, vFile,
                VariableDescriberFunctionArguments(variableText)
            )
        val sessionInfo = NotebookSessionAnalyzerFunction
            .invokeWithParameters(psiElement.project, vFile)


        return listOf(
            chatAttachmentWithSourcePosition
                .createChatAttachmentWithSamePosition(
                    "kotlin variable information",
                    variableAdditionalInfo.response
            ),
            chatAttachmentWithSourcePosition
                .createChatAttachmentWithSamePosition(
                    "Kotlin Notebook session info",
                    sessionInfo.response
            ),
            refactoringsAttachmentInfo
        )
    }

}

internal fun ChatAttachmentWithSourcePosition.createChatAttachmentWithSamePosition(
    name: String,
    data: String
): ChatAttachmentWithSourcePosition {
    return ChatAttachmentWithSourcePosition(
        NullablePointer,
        ChatAttachment(
            name,
            data,
            ChatAttachment.Kind.Visible
        ),
        depth, type
    )
}