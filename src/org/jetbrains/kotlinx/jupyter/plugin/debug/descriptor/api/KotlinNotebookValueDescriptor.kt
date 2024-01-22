// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor.api

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.debugger.ui.tree.ValueDescriptor
import com.intellij.debugger.ui.tree.render.ArrayRenderer
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener
import com.intellij.debugger.ui.tree.render.NodeRenderer
import com.intellij.debugger.ui.tree.render.OnDemandRenderer
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.NlsContexts
import com.sun.jdi.Value
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.function.Function

interface KotlinNotebookValueDescriptor {
    companion object {
        val LOG = thisLogger()
    }

    val isExpandableValue: Boolean

    val debuggerSession: DebuggerSession

    var notebookValueText: String

    fun presentableName(): String

    fun ValueDescriptorImpl.setUpValueIcon(renderer: NodeRenderer, context: EvaluationContextImpl?, labelListener: DescriptorLabelListener) = Unit

    fun NodeRenderer.trySetUpLabelId(descriptorImpl: ValueDescriptorImpl, processImpl: DebugProcessImpl, labelListener: DescriptorLabelListener) = Unit

    fun CompletableFuture<Boolean>.handleCompletionOfExpandableParameter(labelListener: DescriptorLabelListener) = Unit

    @NlsContexts.Label
    fun calculateRepresentationInKotlinNotebook(
        context: EvaluationContextImpl?,
        value: Value?,
        labelListener: DescriptorLabelListener,
        futureRenderer: CompletableFuture<NodeRenderer>,
        futureChildrenRenderer: CompletableFuture<NodeRenderer>,
        descriptorImpl: ValueDescriptorImpl
    ): String {
        DebuggerManagerThreadImpl.assertIsManagerThread()
        val process = debuggerSession.process

        futureRenderer.thenAccept { renderer ->
            if (!OnDemandRenderer.isOnDemandForced(process)) {
                descriptorImpl.setUpValueIcon(renderer, context, labelListener)
            }

            val expandableFuture = futureChildrenRenderer
                .thenCompose(Function<NodeRenderer, CompletionStage<Boolean>> { r: NodeRenderer ->
                    r.isExpandableAsync(
                        value,
                        context,
                        descriptorImpl
                    )
                })

            //set label id
            renderer.trySetUpLabelId(descriptorImpl, process, labelListener)

            try {
                //LOG.warn("Calculate label now ${presentableName()} ")
                notebookValueText =
                    if (renderer is ArrayRenderer) {
                        value.toString()
                    } else renderer.calcLabel(this as ValueDescriptor, null, labelListener)
            }
            catch (ex: EvaluateException) {
                LOG.warn("Exception occuried during computation of label: ", ex)
            }

            // only call labelChanged when we have expandable value
            expandableFuture.handleCompletionOfExpandableParameter(labelListener)
        }.exceptionally { throwable ->
            LOG.error(DebuggerUtilsAsync.unwrap(throwable))
            null
        }

        return notebookValueText
    }

}