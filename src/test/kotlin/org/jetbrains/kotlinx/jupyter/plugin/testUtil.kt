package org.jetbrains.kotlinx.jupyter.plugin

import org.apache.log4j.Logger
import kotlin.reflect.KClass

fun getLogger(clazz: KClass<*>): Logger {
    return Logger.getLogger(clazz.simpleName)!!
}

abstract class BaseTest {
    protected val log = getLogger(this::class)
}
