// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport

import com.intellij.util.containers.ContainerUtil
import kotlin.reflect.KClass
import kotlin.script.experimental.api.KotlinType

/**
 * [KotlinImplicitReceiversList] is the list of previous scripts implicit receivers.
 * It is introduced to reduce the ways of the list modification and add an ability
 * to debug referencing its elements from the internals of scripting plugin implementation
 *
 * @property types Implicit receivers types backing this list
 */
class KotlinImplicitReceiversList(
    private val types: MutableList<KotlinType> = ContainerUtil.createConcurrentList()
) : List<KotlinType> by types {
    fun addClass(kClass: KClass<*>) {
        types.add(KotlinType(kClass))
    }

    fun clear() {
        types.clear()
    }

    override fun get(index: Int): KotlinType {
        return types[index]
    }
}
