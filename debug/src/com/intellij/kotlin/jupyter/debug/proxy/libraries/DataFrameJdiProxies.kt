// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.libraries

import com.intellij.kotlin.jupyter.debug.proxy.JdiDescriptorAwareApiDelegateProxy
import com.intellij.kotlin.jupyter.debug.proxy.JdiFieldAccessPath
import com.intellij.kotlin.jupyter.debug.proxy.JdiMethodInvocationSignature
import com.intellij.kotlin.jupyter.debug.proxy.JdiProxyApiDelegate
import kotlin.reflect.KType


interface DataFrameJdiProxy : JdiProxyApiDelegate {
    @get:JdiFieldAccessPath("nrow")
    val rowsNumber: Int

    @get:JdiFieldAccessPath("columnsMap")
    val columnsNameToIndexData: Map<String, Int>

    @get:JdiMethodInvocationSignature("columnTypes")
    val columnTypes: List<KType>

    @get:JdiMethodInvocationSignature("columnNames")
    val columnNames: List<String>

    @JdiMethodInvocationSignature("get")
    fun get(name: String): DataFrameJdiColumnProxy?

    @get:JdiFieldAccessPath("columns")
    val columns: DataFrameJdiColumnContainerProxy
}


interface DataFrameJdiColumnContainerProxy : JdiDescriptorAwareApiDelegateProxy, List<DataFrameJdiColumnProxy> {
    override fun iterator(): Iterator<DataFrameJdiColumnProxy>
}


interface DataFrameJdiColumnProxy : JdiDescriptorAwareApiDelegateProxy {
    @get:JdiFieldAccessPath("name")
    val name: String

    @get:JdiFieldAccessPath("type")
    val type: KType

    @get:JdiMethodInvocationSignature("hasNulls")
    val hasNulls: Boolean

    // Access it plainly, all the work is done by XCollectionAccessor
    @get:JdiMethodInvocationSignature("getValues")
    val values: JdiDescriptorAwareApiDelegateProxy

    @JdiMethodInvocationSignature("get")
    fun get(index: Int): Any

    @JdiMethodInvocationSignature("get")
    fun get(range: IntRange): DataFrameJdiColumnProxy

    @JdiMethodInvocationSignature("toList")
    fun toList(): List<*>
}