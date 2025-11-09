// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.whenDisposed
import kotlin.reflect.KMutableProperty0

fun <T> KMutableProperty0<T>.setUntilDisposed(disposable: Disposable, value: T) {
    val oldValue = get()
    disposable.whenDisposed { set(oldValue) }
    set(value)
}
