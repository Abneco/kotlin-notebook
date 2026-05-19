package com.intellij.kotlin.jupyter.sql

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

object KotlinJupyterSqlBundle {
    private const val BUNDLE_FQN = "messages.KotlinJupyterSqlBundle"
    private val BUNDLE = DynamicBundle(this::class.java, BUNDLE_FQN)

    @JvmStatic
    fun message(
        key: @PropertyKey(resourceBundle = BUNDLE_FQN) String,
        vararg params: Any,
    ): @Nls String {
        return BUNDLE.getMessage(key, *params)
    }
}
