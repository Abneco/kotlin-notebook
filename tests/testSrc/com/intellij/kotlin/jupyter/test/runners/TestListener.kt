// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.runners

interface TestListener {
    fun beforeSetUp(testInstance: Any){}
    fun afterSetUp(testInstance: Any){}
    fun beforeTearDown(testInstance: Any){}
    fun afterTearDown(testInstance: Any){}
}

interface ListenableTest {
    fun addListener(listener: TestListener)

    fun wrapSetUp(testInstance: Any, setUp: () -> Unit)
    fun wrapTearDown(testInstance: Any, tearDown: () -> Unit)
}

class ListenableTestImpl: ListenableTest {
    private val listeners = mutableListOf<TestListener>()

    override fun addListener(listener: TestListener) {
        listeners.add(listener)
    }

    override fun wrapSetUp(testInstance: Any, setUp: () -> Unit) {
        for (listener in listeners) {
            listener.beforeSetUp(testInstance)
        }
        try {
            setUp()
        } finally {
          for (listener in listeners) {
                listener.afterSetUp(testInstance)
            }
        }
    }

    override fun wrapTearDown(testInstance: Any, tearDown: () -> Unit) {
        for (listener in listeners) {
            listener.beforeTearDown(testInstance)
        }
        try {
            tearDown()
        } finally {
            for (listener in listeners) {
                listener.afterTearDown(testInstance)
            }
            listeners.clear()
        }
    }
}