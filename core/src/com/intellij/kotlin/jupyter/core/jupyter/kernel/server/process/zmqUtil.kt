// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process

import com.intellij.kotlin.jupyter.core.logging.KotlinNotebookLoggerFactory
import org.zeromq.ZMQ
import zmq.Ctx
import zmq.io.IOThread
import zmq.poll.Poller
import java.io.Closeable

private val logger get() = KotlinNotebookLoggerFactory.getInstance(ZMQ::class)

data class ZmqPoller(
    val poller: Poller,
    val workerThread: Thread?,
): Closeable {
    override fun close() {
        poller.stop()
        workerThread?.interrupt()
    }
}

/**
 * This is our best attempt to find leaking threads.
 */
fun getPollersFromContext(context: ZMQ.Context): List<ZmqPoller> {
    val pollers = mutableListOf<ZmqPoller>()
    fun addPoller(poller: Poller) {
        val workerThread = poller.getWorkerThread()
        pollers.add(ZmqPoller(poller, workerThread))
    }

    try {
        val ctx = context.getCtx() ?: return pollers

        val reaper = ctx.getReaper()
        reaper?.getPoller()?.let(::addPoller)

        val ioThreads = ctx.getIoThreads().orEmpty()
        for (ioThread in ioThreads) {
            ioThread.getPoller()?.let(::addPoller)
        }
    } catch (e: Throwable) {
        logger.warn("Error getting ZMQ workers", e)
    }
    return pollers
}

private fun ZMQ.Context.getCtx(): Ctx? = getOwnPrivateField("ctx")
private fun Ctx.getReaper(): Any? = getOwnPrivateField("reaper")
private fun Ctx.getIoThreads(): List<IOThread>? = getOwnPrivateField("ioThreads")
private fun Any.getPoller(): Poller? = getOwnPrivateField("poller")
private fun Poller.getWorkerThread(): Thread? = getPrivateFieldFromSuperclasses("worker")

private inline fun <reified C : Any, reified T> C.getOwnPrivateField(fieldName: String): T? {
    return getPrivateField(this::class.java, fieldName)
}

private inline fun <reified C : Any, reified T> C.getPrivateFieldFromSuperclasses(fieldName: String): T? {
    var currentClass: Class<*> = this::class.java
    while (currentClass != Any::class.java) {
        val value: T? = getPrivateField(currentClass, fieldName)
        if (value != null) return value
        currentClass = currentClass.superclass
    }
    return null
}

private inline fun <reified C : Any, reified T> C.getPrivateField(clazz: Class<*>, fieldName: String): T? {
    return try {
        val field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        field.get(this) as? T
    } catch (e: Throwable) {
        if (logger.isDebugEnabled) {
            logger.debug("Unable to get field $fieldName of $this (class $clazz)", e)
        }
        null
    }
}
