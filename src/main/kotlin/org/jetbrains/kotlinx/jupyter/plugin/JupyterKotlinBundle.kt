package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.JupyterKotlinBundle"

object JupyterKotlinBundle : DynamicBundle(BUNDLE) {
    @JvmStatic
    @Nls
    fun message(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any
    ): String = getMessage(key, *params)
}
