// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener
import com.intellij.debugger.ui.tree.render.NodeRenderer
import com.intellij.debugger.ui.tree.render.NodeRendererImpl
import com.intellij.debugger.ui.tree.render.OnDemandRenderer
import com.sun.jdi.ArrayReference
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.Value
import org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor.api.KotlinNotebookValueDescriptor
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer
import kotlin.coroutines.cancellation.CancellationException

class NotebookArrayElementDescriptor(
    private val debugProcessImpl: DebugProcessImpl,
    private val value: Value,
    arrayReference: ArrayReference,
    index: Int,
) : ArrayElementDescriptorImpl(debugProcessImpl.project, arrayReference, index), KotlinNotebookValueDescriptor {
    override fun calcValue(evaluationContext: EvaluationContextImpl?): Value? {
        return value
    }

    override fun isValueReady(): Boolean {
        return true
    }

    override fun getValue(): Value {
        return value
    }

    override fun isExpandable(): Boolean = _isExpandable

    private var _isExpandable = false

    override val isExpandableValue: Boolean
        get() = _isExpandable

    override val debuggerSession: DebuggerSession
        get() = debugProcessImpl.session

    override var notebookValueText: String
        get() = valueLabel
        set(value) {
            valueLabel = value
        }

    override fun calcRepresentation(context: EvaluationContextImpl?, labelListener: DescriptorLabelListener): String {
        return calculateRepresentationInKotlinNotebook(
            context, value, labelListener,
            getRenderer(debugProcessImpl),
            getChildrenRenderer(debugProcessImpl),
            this
        )
    }

    override fun presentableName(): String = name

    override fun ValueDescriptorImpl.setUpValueIcon(
        renderer: NodeRenderer,
        context: EvaluationContextImpl?,
        labelListener: DescriptorLabelListener
    ) {
        if (!OnDemandRenderer.isOnDemandForced(debugProcessImpl)) {
            try {
                setValueIcon(renderer.calcValueIcon(this, context, labelListener))
            } catch (e: EvaluateException) {
                LOG.info(e)
                setValueIcon(null)
            }
        }
    }

    override fun NodeRenderer.trySetUpLabelId(
        descriptorImpl: ValueDescriptorImpl,
        processImpl: DebugProcessImpl,
        labelListener: DescriptorLabelListener
    ) {
        if (isShowIdLabel && this is NodeRendererImpl) {
            idLabel = calcIdLabel(descriptorImpl, processImpl, labelListener)
        }
    }


    override fun CompletableFuture<Boolean>.handleCompletionOfExpandableParameter(labelListener: DescriptorLabelListener) {
        whenComplete(BiConsumer { res: Boolean, ex: Throwable? ->
            var ex = ex
            if (ex == null) {
                _isExpandable = res
                LOG.debug("Value: ${presentableName()}, Is expandable: $isExpandableValue, valueText: $notebookValueText")
            } else {
                ex = DebuggerUtilsAsync.unwrap(ex)
                if (ex is EvaluateException) {
                    LOG.warn(Throwable(ex))
                } else if (ex !is CancellationException && ex !is VMDisconnectedException) {
                    LOG.error(Throwable(ex))
                }
            }
            labelListener.labelChanged()
        })
    }
}