package org.jetbrains.kotlinx.jupyter.plugin

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
    private val types: MutableList<KotlinType> = mutableListOf()
) : List<KotlinType> by types {
    fun addClass(kClass: KClass<*>) {
        types.add(KotlinType(kClass))
    }

    override fun iterator(): Iterator<KotlinType> {
        return types.iterator()
    }

    override fun get(index: Int): KotlinType {
        return types[index]
    }
}
