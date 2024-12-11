// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.codeInsight.metaLanguage

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.ws.JupyterWebSocketClientClosedException
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterCompleteRequestMessageBuilder
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.connections.execution.notebook.JupyterRuntimeService
import com.intellij.jupyter.core.jupyter.connections.http.JupyterRestClientErrorResponseException
import com.intellij.kotlin.jupyter.core.util.deserialize
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Key
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlinx.jupyter.messaging.CompleteReply
import java.net.ConnectException
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class KotlinMagicsRuntimeCompletionContributor: CompletionContributor(), DumbAware {
    private val nonFinishedRequests: AtomicInteger = AtomicInteger(0)

    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                val psiFile = parameters.originalFile
                val notebookVirtualFile = getVirtualFile(psiFile) ?: return
                val project = psiFile.project
                val session = JupyterRuntimeService.getInstance(project).getSession(notebookVirtualFile.file) ?: return

                try {
                    if (nonFinishedRequests.getAndIncrement() > 0) return
                    val otherResults = result.runRemainingContributors(parameters, true)
                    ProgressManager.checkCanceled()
                    val editor = parameters.editor
                    val caretOffset = editor.caretModel.offset

                    val text = psiFile.text

                    ApplicationUtil.runWithCheckCanceled(Callable {
                        try {
                            sendCompleteRequestMessage(session, result, otherResults, text, caretOffset)
                        }
                        catch (e: Throwable) {
                            when (e) {
                                is ProcessCanceledException -> throw e
                                is InterruptedException -> Unit
                                is JupyterWebSocketClientClosedException -> Unit
                                is JupyterRestClientErrorResponseException -> Unit
                                is ConnectException -> Unit
                                else -> {
                                    LOG.warn(COMPLETION_LOG_MESSAGE, e)
                                }
                            }
                        }
                    }, ProgressManager.getInstance().progressIndicator)
                }
                finally {
                    nonFinishedRequests.decrementAndGet()
                }
            }
        })
    }

    fun getVirtualFile(psiFile: PsiFile) : BackedNotebookVirtualFile? {
        val vFile = psiFile.virtualFile
        val originalFile = if (vFile is VirtualFileWindow) vFile.delegate else vFile
        return originalFile?.let(BackedNotebookVirtualFile::takeIfBacked)
    }

    fun sendCompleteRequestMessage(session: JupyterNotebookSession, result: CompletionResultSet, otherResults: Set<CompletionResult>,
                                   code: String, cursorPos: Int
    ) {
        val message = JupyterCompleteRequestMessageBuilder(code, cursorPos, session.sessionId).build()
        val replyNotifier = CountDownLatch(1)
        session.sendMessage(message, object : JupyterExecutionCallbackAdapter() {
            override fun onCompleteReply(message: JupyterMessage) {
                val otherCompletionStrings = otherResults.map { it.lookupElement.lookupString }.toSet()
                val lookupElements = getLookupElements(message, otherCompletionStrings)
                result.addAllElements(lookupElements)
                replyNotifier.countDown()
                finalizeCallback()
            }
        })
        replyNotifier.await()
    }

    fun getLookupElements(message: JupyterMessage, stringsToExclude: Set<String>): List<LookupElement> {
        val reply = message.messageContent.deserialize<CompleteReply>() ?: return emptyList()
        return reply.metadata.extended.mapNotNull {
            if(it.text in stringsToExclude) null
            else LookupElementBuilder.create(it.text).withTypeText(it.tail).apply { putUserData(RUNTIME_COMPLETION, true) }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(KotlinMagicsRuntimeCompletionContributor::class.java)
        private const val COMPLETION_LOG_MESSAGE = "Failed to send Jupyter completion message:"

        private val RUNTIME_COMPLETION = Key.create<Boolean>("ELEMENT_IS_FROM_RUNTIME")
    }
}
