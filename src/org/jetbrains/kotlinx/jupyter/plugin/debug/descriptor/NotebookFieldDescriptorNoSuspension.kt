// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl
import com.intellij.debugger.ui.tree.render.ArrayRenderer
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener
import com.intellij.debugger.ui.tree.render.NodeRenderer
import com.intellij.debugger.ui.tree.render.NodeRendererImpl
import com.intellij.debugger.ui.tree.render.OnDemandRenderer
import com.intellij.openapi.project.Project
import com.sun.jdi.Field
import com.sun.jdi.ObjectReference
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.Value
import org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor.api.KotlinNotebookValueDescriptor
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionStage
import java.util.function.BiConsumer
import java.util.function.Function

class NotebookFieldDescriptorNoSuspension(
    private val session: DebuggerSession,
    val virtualFile: BackedNotebookVirtualFile?,
    project: Project,
    objectReference: ObjectReference,
    field: Field,
    private val value: Value?
) : FieldDescriptorImpl(project, objectReference, field), KotlinNotebookValueDescriptor {
    override fun calcValue(evaluationContext: EvaluationContextImpl?): Value? {
        return value
    }

    override fun getValue(): Value? = value

    override fun isValueReady(): Boolean = true

    private var _isExpandable = false

    override fun isExpandable(): Boolean {
        return isExpandableValue
    }

    val sourcePosition: SourcePosition? by lazy {
        NotebookVariableDescriptorPositionResolver.resolveTo(project, virtualFile?.file, this)
    }

    override fun calcRepresentation(context: EvaluationContextImpl?, labelListener: DescriptorLabelListener): String {
        DebuggerManagerThreadImpl.assertIsManagerThread()
        val process = session.process

        getRenderer(process).thenAccept { renderer ->
            if (!OnDemandRenderer.isOnDemandForced(process)) {
                try {
                    setValueIcon(renderer.calcValueIcon(this, context, labelListener))
                } catch (e: EvaluateException) {
                    LOG.info(e)
                    setValueIcon(null)
                }
            }

            val expandableFuture = getChildrenRenderer(process)
                    .thenCompose(Function<NodeRenderer, CompletionStage<Boolean>> { r: NodeRenderer ->
                        r.isExpandableAsync(
                            value,
                            context,
                            this
                        )
                    })

            //set label id
            if (isShowIdLabel && renderer is NodeRendererImpl) {
                idLabel = renderer.calcIdLabel(this, process, labelListener)
            }

            try {
                //LOG.warn("Calculate label now ${name}")
                setValueLabel(
                    if (renderer is ArrayRenderer) {
                        value.toString()
                    } else renderer.calcLabel(this, null, labelListener)
                )
            }
            catch (ex: EvaluateException) {
                LOG.warn("Exception occurred during computation of label: ", ex)
                setValueLabelFailed(ex)
            }

            // only call labelChanged when we have expandable value
            expandableFuture.whenComplete(BiConsumer<Boolean, Throwable> { res: Boolean, ex: Throwable? ->
                var ex = ex
                if (ex == null) {
                    _isExpandable = res
                    LOG.debug("Value: ${getName()}, Is expandable: $isExpandableValue, valueText: $valueText")
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
        }.exceptionally { throwable ->
            LOG.error(DebuggerUtilsAsync.unwrap(throwable))
            null
        }

        return valueText
    }

    override val isExpandableValue: Boolean
        get() = _isExpandable

    override val debuggerSession: DebuggerSession
        get() = session

    override var notebookValueText: String
        get() = valueText
        set(value) {
            valueLabel = value
        }

    override fun presentableName(): String = name
}