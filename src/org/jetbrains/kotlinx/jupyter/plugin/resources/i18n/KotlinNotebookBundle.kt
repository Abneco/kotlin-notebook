// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.resources.i18n

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

@NonNls
private const val BUNDLE = "messages.KotlinNotebookBundle"

internal object KotlinNotebookBundle {
    private val bundle = DynamicBundle(KotlinNotebookBundle::class.java, BUNDLE)

    @JvmStatic
    @Nls
    fun message(
      @PropertyKey(resourceBundle = BUNDLE) key: String,
      vararg params: Any
    ): String = bundle.getMessage(key, *params)

    @JvmStatic
    @Nls
    fun messageWithDefaultValue(
      @PropertyKey(resourceBundle = BUNDLE) key: String,
      @Nls defaultValue: String,
      vararg params: Any
    ): String = bundle.messageOrDefault(key = key, defaultValue = defaultValue, params = params)!!

    @JvmStatic
    fun messagePointer(
      @PropertyKey(resourceBundle = BUNDLE) key: String,
      vararg params: Any
    ): Supplier<@Nls String> = bundle.getLazyMessage(key, *params)
}