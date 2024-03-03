// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.Disposable
import java.util.EventListener
import kotlin.reflect.KMutableProperty1

interface ObservableStateComponent<StateT, ListenerT : EventListener> {
    fun addListener(listener: ListenerT, disposable: Disposable)
    fun removeListener(listener: ListenerT)
    fun <InternalT, ExternalT> prop(stateProperty: KMutableProperty1<StateT, InternalT>, internalToExternal: (InternalT) -> ExternalT, externalToInternal: (ExternalT) -> InternalT): StatePropertyDelegate<StateT, ListenerT, ExternalT>
}

@Suppress("unused")
fun <StateT, ListenerT : EventListener, InternalT : ExternalT, ExternalT> ObservableStateComponent<StateT, ListenerT>.propWidening(stateProperty: KMutableProperty1<StateT, InternalT>, externalToInternal: (ExternalT) -> InternalT): StatePropertyDelegate<StateT, ListenerT, ExternalT> {
    return prop(stateProperty, { it }, externalToInternal)
}

fun <StateT, ListenerT : EventListener, InternalT, ExternalT : InternalT> ObservableStateComponent<StateT, ListenerT>.propNarrowing(stateProperty: KMutableProperty1<StateT, InternalT>, internalToExternal: (InternalT) -> ExternalT): StatePropertyDelegate<StateT, ListenerT, ExternalT> {
    return prop(stateProperty, internalToExternal) { it }
}

fun <StateT, ListenerT : EventListener, PropertyT> ObservableStateComponent<StateT, ListenerT>.prop(stateProperty: KMutableProperty1<StateT, PropertyT>): StatePropertyDelegate<StateT, ListenerT, PropertyT> {
    return prop(stateProperty, { it }, { it })
}
