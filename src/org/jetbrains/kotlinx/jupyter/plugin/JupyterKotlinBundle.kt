package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.BundleBase
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

    @JvmStatic
    @Nls
    fun messageWithDefaultValue(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        @Nls defaultValue: String,
        vararg params: Any
    ): String = BundleBase.messageOrDefault(getResourceBundle(javaClass.classLoader), key, defaultValue, *params)
}
