// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots.i18n

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.KotlinNotebookPlotsBundle"

object KotlinNotebookPlotsBundle {
    private val bundle = DynamicBundle(KotlinNotebookPlotsBundle::class.java, BUNDLE)

    @JvmStatic
    @Nls
    fun message(
      @PropertyKey(resourceBundle = BUNDLE) key: String,
      vararg params: Any
    ): String = bundle.getMessage(key, *params)
}
