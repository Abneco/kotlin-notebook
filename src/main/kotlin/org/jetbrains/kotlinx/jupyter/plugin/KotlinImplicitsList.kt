package org.jetbrains.kotlinx.jupyter.plugin

import kotlin.reflect.KClass
import kotlin.script.experimental.api.KotlinType

class KotlinImplicitsList(
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
