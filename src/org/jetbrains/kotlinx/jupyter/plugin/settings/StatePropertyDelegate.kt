// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import kotlin.reflect.KProperty

interface StatePropertyDelegate<StateT, ListenerT, ExternalT> {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): ExternalT

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: ExternalT)

    fun onChange(action: ListenerT.() -> Unit): StatePropertyDelegate<StateT, ListenerT, ExternalT>
    fun onChange(action: ListenerT.(old: ExternalT, new: ExternalT) -> Unit): StatePropertyDelegate<StateT, ListenerT, ExternalT>
}
