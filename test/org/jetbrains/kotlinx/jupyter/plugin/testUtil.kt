package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.openapi.diagnostic.Logger
import kotlin.reflect.KClass

fun getLogger(clazz: KClass<*>): Logger {
    return Logger.getInstance(clazz.simpleName!!)
}

abstract class BaseTest {
    protected val log = getLogger(this::class)
}
