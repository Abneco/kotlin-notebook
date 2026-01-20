// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.util.connection

import com.intellij.debugger.engine.DebugProcess
import com.sun.jdi.VMDisconnectedException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.UndeclaredThrowableException

/**
 * Checks if a connection with the target VM is established and stable
 * to make computations
 */
val DebugProcess.isConnectionAlive: Boolean
    get() {
        if (isDetaching || isDetached || !isAttached) return false

        val handler = processHandler
        return handler != null && !handler.isProcessTerminated
    }

val Throwable.isVMDisconnectedException: Boolean
    get() {
        if (this is VMDisconnectedException) return true

        val cause = when (this) {
            is InvocationTargetException -> this.targetException
            is UndeclaredThrowableException -> this.undeclaredThrowable
            else -> this.cause
        }

        return cause is VMDisconnectedException
    }

/**
 * Checks if the exception is a wrapped proxy invocation exception
 * that typically occurs when VM is disconnecting or in an unstable state.
 * These exceptions should be handled gracefully by returning null.
 */
val Throwable.isProxyInvocationException: Boolean
    get() {
        val cause = when (this) {
            is InvocationTargetException -> this.targetException
            is UndeclaredThrowableException -> this.undeclaredThrowable
            else -> this.cause
        }

        return cause is InvocationTargetException || cause is IllegalStateException
    }
