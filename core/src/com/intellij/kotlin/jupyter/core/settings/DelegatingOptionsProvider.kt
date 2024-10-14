// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import java.util.*
import kotlin.reflect.KMutableProperty1

abstract class DelegatingOptionsProvider<StateT: BaseState, ListenerT: EventListener>(
    initialState: StateT,
    listenerClass: Class<ListenerT>,
): SimplePersistentStateComponent<StateT>(initialState), ObservableStateComponent<StateT, ListenerT> {
    private val propertyFactory = StatePropertyDelegateFactory(::getState, listenerClass)

    override fun addListener(listener: ListenerT, disposable: Disposable) {
        propertyFactory.addListener(listener, disposable)
    }

    override fun removeListener(listener: ListenerT) {
        propertyFactory.removeListener(listener)
    }

    override fun <InternalT, ExternalT> prop(
        stateProperty: KMutableProperty1<StateT, InternalT>,
        internalToExternal: (InternalT) -> ExternalT,
        externalToInternal: (ExternalT) -> InternalT
    ): StatePropertyDelegate<StateT, ListenerT, ExternalT> {
        return propertyFactory.prop(stateProperty, internalToExternal, externalToInternal)
    }

    override fun loadState(state: StateT) {
        val oldState = getState()
        super.loadState(state)
        val newState = getState()
        propertyFactory.stateLoaded(oldState, newState)
    }
}
