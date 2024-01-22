// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.frame

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.FullValueEvaluatorProvider
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.JavaValue.createPresentation
import com.intellij.debugger.engine.SourcePositionProvider
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.ui.impl.DebuggerTreeRenderer
import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl
import com.intellij.debugger.ui.impl.watch.MessageDescriptor
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl
import com.intellij.debugger.ui.impl.watch.NodeDescriptorProvider
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.debugger.ui.tree.ArrayElementDescriptor
import com.intellij.debugger.ui.tree.DebuggerTreeNode
import com.intellij.debugger.ui.tree.NodeDescriptorFactory
import com.intellij.debugger.ui.tree.NodeManager
import com.intellij.debugger.ui.tree.ValueDescriptor
import com.intellij.debugger.ui.tree.render.ArrayRenderer
import com.intellij.debugger.ui.tree.render.ChildrenBuilder
import com.intellij.debugger.ui.tree.render.CompoundReferenceRenderer
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener
import com.intellij.debugger.ui.tree.render.OnDemandRenderer
import com.intellij.debugger.ui.tree.render.Renderer
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XNavigatable
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XErrorValuePresentation
import com.intellij.xdebugger.impl.pinned.items.PinToTopMemberValue
import com.intellij.xdebugger.impl.pinned.items.PinToTopParentValue
import com.intellij.xdebugger.impl.ui.XValueTextProvider
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor.NotebookArrayElementDescriptor
import org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor.NotebookVariableStateDescriptor
import org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor.api.KotlinNotebookValueDescriptor
import javax.swing.Icon
import kotlin.math.max

class NotebookVariableFieldValue(
    private val parent: NotebookVariableFieldValue?,
    private val valueDescriptor: ValueDescriptorImpl,
    private val debugProcessImpl: DebugProcessImpl,
    private val nodeManager: NodeManagerImpl?,
    contextSet: Boolean
) : XNamedValue(valueDescriptor.calcValueName()), NodeDescriptorProvider,
    XValueTextProvider, PinToTopParentValue, PinToTopMemberValue
    //: JavaValue(
    //parent,
    //valueDescriptor,
    //debugProcessImpl.suspendManager.pausedContext?.evaluationContext
    //    ?: debugProcessImpl.suspendManager.pushSuspendContext(EventRequest.SUSPEND_NONE, 1).evaluationContext,
    //nodeManager, contextSet)

{
    companion object {
        private val LOG = thisLogger()

        private fun ValueDescriptorImpl.toNotebookValueDescriptor(debugProcessImpl: DebugProcessImpl, parentDescriptor: NotebookVariableStateDescriptor?): KotlinNotebookValueDescriptor? {
            when (this) {
                is NotebookVariableStateDescriptor -> return this
                is ArrayElementDescriptorImpl -> {
                    return NotebookArrayElementDescriptor(debugProcessImpl, array.getValue(index), array, index)
                }
                else -> {}
            }
            if (this !is FieldDescriptorImpl) return null
            val parentValue = `object`
            val field = field
            val value = parentValue.getValue(field)

            return NotebookVariableStateDescriptor(debugProcessImpl.session, parentDescriptor?.virtualFile, debugProcessImpl.project, parentValue, field, value)
        }
    }

    private var canBePinned = doComputeCanBePinned()

    override val tag: String?
        get() {
            val type = valueDescriptor.getType()
            return type?.name()
        }

    override fun canBePinned(): Boolean = canBePinned

    private fun doComputeCanBePinned(): Boolean {
        if (valueDescriptor is ArrayElementDescriptor) {
            return false
        }
        return parent != null
    }

    private fun computeChildren(remainingElements: Int, node: XCompositeNode) {
        valueDescriptor.getChildrenRenderer(debugProcessImpl).thenAccept { renderer ->
            try {
                renderer.buildChildren(valueDescriptor.value, object : ChildrenBuilder {
                    override fun getDescriptorManager(): NodeDescriptorFactory? = this@NotebookVariableFieldValue.nodeManager
                    override fun getNodeManager(): NodeManager? = this@NotebookVariableFieldValue.nodeManager as? NodeManager
                    override fun getParentDescriptor(): ValueDescriptor = valueDescriptor

                    override fun setChildren(children: MutableList<out DebuggerTreeNode>) {
                        addChildren(children, true)
                    }

                    override fun addChildren(children: MutableList<out DebuggerTreeNode>, last: Boolean) {
                        try {
                            var childrenList = XValueChildrenList.EMPTY
                            if (!children.isEmpty()) {
                                childrenList = XValueChildrenList(children.size)
                                for (treeNode in children) {
                                    val descriptor = treeNode.getDescriptor()
                                    if (descriptor is ValueDescriptorImpl) {
                                        // Value is calculated already in NodeManagerImpl
                                        childrenList.add(
                                            NotebookVariableFieldValue(
                                                this@NotebookVariableFieldValue,
                                                descriptor.toNotebookValueDescriptor(
                                                    debugProcessImpl,
                                                    this@NotebookVariableFieldValue.valueDescriptor as? NotebookVariableStateDescriptor
                                                ) as? ValueDescriptorImpl
                                                    ?: descriptor,
                                                debugProcessImpl,
                                                nodeManager as NodeManagerImpl, false
                                            )
                                        )
                                    } else if (descriptor is MessageDescriptor) {
                                        childrenList.add(
                                            JavaStackFrame.createMessageNode(
                                                descriptor.getLabel(),
                                                DebuggerTreeRenderer.getDescriptorIcon(descriptor)
                                            )
                                        )
                                    }
                                }
                            }
                            node.addChildren(childrenList, last)
                        } catch (ex: Exception) {
                            LOG.warn("Exception during computation of children for node: $name, ex: ", ex)
                        }
                    }

                    override fun addChildren(children: XValueChildrenList, last: Boolean) {
                        node.addChildren(children, last)
                    }

                    override fun tooManyChildren(remaining: Int) {
                        node.tooManyChildren(remaining, Runnable { computeChildren(remaining, node) })
                    }

                    override fun tooManyChildren(remaining: Int, addNextChildren: Runnable) {
                        node.tooManyChildren(remaining, addNextChildren)
                    }

                    override fun setAlreadySorted(alreadySorted: Boolean) {
                        node.setAlreadySorted(alreadySorted)
                    }

                    override fun setErrorMessage(errorMessage: String) {
                        node.setErrorMessage(errorMessage)
                    }

                    override fun setErrorMessage(errorMessage: String, link: XDebuggerTreeNodeHyperlink?) {
                        node.setErrorMessage(errorMessage, link)
                    }

                    override fun setMessage(message: String, icon: Icon?, attributes: SimpleTextAttributes, link: XDebuggerTreeNodeHyperlink?) {
                        node.setMessage(message, icon, attributes, link)
                    }

                    override fun isObsolete(): Boolean = node.isObsolete

                    override fun initChildrenArrayRenderer(renderer: ArrayRenderer?, arrayLength: Int) {
                        renderer!!.START_INDEX = 0
                        if (remainingElements >= 0) {
                            renderer.START_INDEX = max(0.0, (arrayLength - remainingElements).toDouble()).toInt()
                        }
                    }
                }, null)
            } catch (ex: Exception) {
                LOG.warn("Exception during children computation: ", ex)
            }
        }

    }

    override fun computeChildren(node: XCompositeNode) {
        if (node.isObsolete) {
            LOG.debug("Node is obsolete, $node, $name")
            return
        }
        debugProcessImpl.managerThread.invoke(PrioritizedTask.Priority.NORMAL) {
            computeChildren(-1, node)
        }

        //super.computeChildren(node)
    }
    private fun isOnDemand(): Boolean {
        return OnDemandRenderer.ON_DEMAND_CALCULATED.isIn(valueDescriptor)
    }

    private fun isCalculated(): Boolean {
        return OnDemandRenderer.isCalculated(valueDescriptor)
    }

    override fun computePresentation(node: XValueNode, place: XValuePlace) {
        if (isOnDemand() && !isCalculated()) {
            LOG.debug("Apply on demand")
            valueDescriptor.applyOnDemandPresentation(node)
            return
        }


        debugProcessImpl.managerThread.schedule(PrioritizedTask.Priority.NORMAL) {
            valueDescriptor.updateRepresentationNoNotify(null, object : DescriptorLabelListener {
                override fun labelChanged() {
                    trySetUpPresentation(node, place)
                }
            })

            trySetUpPresentation(node, place)
        }
    }

    private fun trySetUpPresentation(node: XValueNode, place: XValuePlace) {
        try {
            val nodeIcon = if (place == XValuePlace.TOOLTIP)
                valueDescriptor.valueIcon
            else DebuggerTreeRenderer.getValueIcon(
                valueDescriptor,
                parent?.valueDescriptor
            )

            val lastRenderer = valueDescriptor.lastRenderer
            var fullEvaluatorSet = setFullValueEvaluator(lastRenderer, node)
            if (!fullEvaluatorSet && lastRenderer is CompoundReferenceRenderer) {
                setFullValueEvaluator(lastRenderer.labelRenderer, node)
            }


            val presentation = createPresentation(valueDescriptor)
            LOG.debug("Set presentation for $name, presentation: ${presentation}, ${valueDescriptor.valueLabel}, isExpandable: ${valueDescriptor.isExpandable}")
            node.setPresentation(nodeIcon, presentation, valueDescriptor.isExpandable)
        } catch (ex: Exception) {
            LOG.warn("Exception during computation presentation: ", ex)
            node.setPresentation(null, XErrorValuePresentation(ex.message ?: "Exception during computation"), false)
        }
    }

    private fun setFullValueEvaluator(renderer: Renderer?, node: XValueNode): Boolean {
        if (renderer is FullValueEvaluatorProvider) {
            val evaluator = (renderer as FullValueEvaluatorProvider).getFullValueEvaluator(null, valueDescriptor)
            if (evaluator != null) {
                node.setFullValueEvaluator(evaluator)
                return true
            }
        }
        return false
    }


    override fun getDescriptor(): NodeDescriptorImpl = valueDescriptor

    override fun getValueText(): String? = valueDescriptor.valueText

    override fun shouldShowTextValue(): Boolean {
        if (valueDescriptor.isValueReady) {
            return valueDescriptor.isString()
        }
        return false
    }

    override fun computeSourcePosition(navigatable: XNavigatable) {
        debugProcessImpl.managerThread.schedule(PrioritizedTask.Priority.NORMAL) {
            runReadAction {
                val position = SourcePositionProvider.getSourcePosition(descriptor, debugProcessImpl.project, debugProcessImpl.debuggerContext, false)
                navigatable.setSourcePosition(DebuggerUtilsEx.toXSourcePosition(position))
            }
        }
    }



    fun reBuild(node: XValueNodeImpl) {
        DebuggerManagerThreadImpl.assertIsManagerThread()
        node.invokeNodeUpdate(Runnable {
            node.clearChildren()
            computePresentation(node, XValuePlace.TREE)
        })
    }
}