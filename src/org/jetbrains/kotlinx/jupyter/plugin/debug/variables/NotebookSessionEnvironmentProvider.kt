// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.variables

import com.intellij.debugger.engine.jdi.VirtualMachineProxy
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.sun.jdi.Field
import com.sun.jdi.IntegerValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile


internal interface NotebookSessionEnvironmentProvider {
    val variableStateReferenceProvider: (VirtualMachineProxy) -> ObjectReference?
    val notebookReferenceProvider: (VirtualMachineProxy) -> ObjectReference?
    val variableValueFromStateProvider: (Value, String) -> Pair<ObjectReference?, Field>?

    val variableStateFirstEntryAndSizeProvider: (ObjectReference) -> Pair<ObjectReference?, Int>
}


internal class NotebookSessionNoSuspensionEnvironmentProvider(
    private val virtualFile: BackedNotebookVirtualFile
) : NotebookSessionEnvironmentProvider {
    override val notebookReferenceProvider: (VirtualMachineProxy) -> ObjectReference?
        get() = ::retrieveNotebookReference
    override val variableStateReferenceProvider: (VirtualMachineProxy) -> ObjectReference?
            = ::retrieveVariablesStateReference
    override val variableValueFromStateProvider: (Value, String) -> Pair<ObjectReference?, Field>
        get() = ::retrieveValueFromVariableState
    override val variableStateFirstEntryAndSizeProvider: (ObjectReference) -> Pair<ObjectReference?, Int>
        get() = ::retrieveMapEntryReferenceAndVariablesSize

    private fun retrieveNotebookReference(virtualMachine: VirtualMachineProxy): ObjectReference? {
        if (virtualMachine !is VirtualMachineProxyImpl) return null
        val notebookClass = virtualMachine.classesByNameProvider.get("org.jetbrains.kotlinx.jupyter.NotebookImpl").firstOrNull() ?: return null
        return notebookClass.instances(1).firstOrNull()
    }

    private fun retrieveVariablesStateReference(virtualMachine: VirtualMachineProxy): ObjectReference? {
        if (virtualMachine !is VirtualMachineProxyImpl) return null
        val notebookReference = retrieveNotebookReference(virtualMachine) ?: return null

        val sharedContextField = notebookReference.referenceType().fieldByName("sharedReplContext")
        val sharedContextReference = notebookReference.getValue(sharedContextField) as ObjectReference
        val evaluatorField = sharedContextReference.referenceType().fieldByName("evaluator")
        val evaluatorImpl = sharedContextReference.getValue(evaluatorField) as ObjectReference

        val variablesHolderField = evaluatorImpl.referenceType().fieldByName("variablesHolder")

        return evaluatorImpl.getValue(variablesHolderField) as ObjectReference
    }

    private fun retrieveValueFromVariableState(variablesState: Value, variableName: String): Pair<ObjectReference?, Field> {
        val objectReference = variablesState as ObjectReference
        if (objectReference.referenceType().name() != "org.jetbrains.kotlinx.jupyter.api.VariableStateImpl") {
            throw IllegalArgumentException("Value retrieval is possible from instances of VariableStateImpl")
        }

        // another way round by scriptInstance and field reflection
        val scriptInstance = objectReference.referenceType().fieldByName("scriptInstance").let {
            objectReference.getValue(it) as ObjectReference
        }
        val suitableFieldAccessor = scriptInstance.referenceType().fieldByName(variableName)

        return scriptInstance to suitableFieldAccessor
    }


    private fun retrieveMapEntryReferenceAndVariablesSize(variablesStateReference: ObjectReference): Pair<ObjectReference?, Int> {
        val declaringClass = variablesStateReference.referenceType()
        if (declaringClass.name() != "java.util.LinkedHashMap") {
            throw UnsupportedOperationException("VariablesState shall be an instance of LinkedMap")
        }
        val currentSize = (variablesStateReference.getValue(declaringClass.fieldByName("size")) as? IntegerValue)?.value()
        val headField = declaringClass.fieldByName("head")
        if (currentSize == null) {
            throw IllegalArgumentException("Could not retrieve size from VariablesState")
        }
        return variablesStateReference.getValue(headField) as? ObjectReference to currentSize
    }
}