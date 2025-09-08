// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings

import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher
import java.util.*
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty

private class StatePropertyDelegateImpl<StateT, ListenerT: EventListener, InternalT, ExternalT>(
    private val stateProvider: () -> StateT,
    private val eventDispatcher: EventDispatcher<ListenerT>,
    val stateProperty: KMutableProperty1<StateT, InternalT>,
    private val internalToExternal: (InternalT) -> ExternalT,
    private val externalToInternal: (ExternalT) -> InternalT,
    private var onChange: ListenerT.(oldValue: ExternalT, newValue: ExternalT) -> Unit = { _, _ -> },
) : StatePropertyDelegate<StateT, ListenerT, ExternalT> {

    fun getValue(state: StateT): ExternalT {
        return internalToExternal(stateProperty.get(state))
    }

    fun getCurrentValue(): ExternalT {
        return getValue(stateProvider())
    }

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): ExternalT {
        return getCurrentValue()
    }

    /**
     * This method gives no guarantees that [fireChangeEvent] would provide accurate 'old' value,
     * if [ExternalT] is a sort of concurrent collection.
     */
    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: ExternalT) {
        val oldValue = getCurrentValue()
        //  NB: 'oldValue' might be updated after setter execution
        val isChanged = oldValue != value
        stateProperty.set(stateProvider(), externalToInternal(value))
        if (isChanged) {
            fireChangeEvent(oldValue, value)
        }
    }

    fun fireChangeEvent(oldValue: ExternalT, newValue: ExternalT) {
        eventDispatcher.multicaster.onChange(oldValue, newValue)
    }

    fun checkAndFireChangeEvent(oldState: StateT, newState: StateT) {
        val oldValue = getValue(oldState)
        val newValue = getValue(newState)
        if (oldValue != newValue) {
            fireChangeEvent(oldValue, newValue)
        }
    }

    override fun onChange(action: ListenerT.() -> Unit): StatePropertyDelegate<StateT, ListenerT, ExternalT> {
        onChange = { _, _ -> action() }
        return this
    }

    override fun onChange(action: ListenerT.(old: ExternalT, new: ExternalT) -> Unit): StatePropertyDelegate<StateT, ListenerT, ExternalT> {
        onChange = action
        return this
    }
}

class StatePropertyDelegateFactory<StateT, ListenerT: EventListener>(
    private val stateProvider: () -> StateT,
    listenerClass: Class<ListenerT>,
) : ObservableStateComponent<StateT, ListenerT> {
    private val eventDispatcher = EventDispatcher.create(listenerClass)
    private val properties = mutableListOf<StatePropertyDelegateImpl<StateT, ListenerT, *, *>>()

    override fun addListener(listener: ListenerT, disposable: Disposable) {
        eventDispatcher.addListener(listener, disposable)
    }

    override fun removeListener(listener: ListenerT) {
        eventDispatcher.removeListener(listener)
    }

    fun stateLoaded(oldState: StateT, newState: StateT) {
        for (property in properties) {
            property.checkAndFireChangeEvent(oldState, newState)
        }
    }

    private fun <InternalT, ExternalT> addProp(stateProperty: KMutableProperty1<StateT, InternalT>, internalToExternal: (InternalT) -> ExternalT, externalToInternal: (ExternalT) -> InternalT): StatePropertyDelegateImpl<StateT, ListenerT, InternalT, ExternalT> {
        return StatePropertyDelegateImpl(
            stateProvider,
            eventDispatcher,
            stateProperty,
            internalToExternal,
            externalToInternal,
        ).also {
            properties.add(it)
        }
    }

    override fun <InternalT, ExternalT> prop(stateProperty: KMutableProperty1<StateT, InternalT>, internalToExternal: (InternalT) -> ExternalT, externalToInternal: (ExternalT) -> InternalT): StatePropertyDelegate<StateT, ListenerT, ExternalT> {
        return addProp(stateProperty, internalToExternal, externalToInternal)
    }
}
