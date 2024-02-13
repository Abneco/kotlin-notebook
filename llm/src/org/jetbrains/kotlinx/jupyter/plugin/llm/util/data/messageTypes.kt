// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.llm.util.data

import com.google.gson.Gson


internal inline fun <reified T> String.parseFromJson(): T {
    val gson = Gson()
    return gson.fromJson(this, T::class.java)
}

data class SessionSummarizationFunctionArguments(
    val targetFile: String?
)

data class VariableDescriberFunctionArguments(
    val variableName: String
)