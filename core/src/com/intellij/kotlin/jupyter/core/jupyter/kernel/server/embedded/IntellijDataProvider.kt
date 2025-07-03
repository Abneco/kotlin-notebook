// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * A data provider class that is integrated with IntelliJ and manages session-specific properties
 * that are disposed of when the instance is disposed.
 *
 * @param currentProject The active IntelliJ project context associated with the current Jupyter session.
 */
class IntellijDataProvider(
    currentProject: Project,
): Disposable {
    val currentProject: Project by property(currentProject)

    override fun dispose() {
    }

    fun <T: Any> property(value: T): ReadOnlyProperty<IntellijDataProvider, T> {
        return object: Disposable, ReadOnlyProperty<IntellijDataProvider, T> {
            private var currentValue: T? = value

            override fun dispose() {
                currentValue = null
            }

            override fun getValue(
                thisRef: IntellijDataProvider,
                property: KProperty<*>
            ): T {
                return currentValue ?: error("Unable to get `${property.name}`. Session data provider was already disposed")
            }
        }.apply {
            Disposer.register(this@IntellijDataProvider, this)
        }
    }
}
