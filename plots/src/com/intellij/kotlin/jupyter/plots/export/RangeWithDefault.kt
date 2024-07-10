// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots.export

class RangeWithDefault<T: Comparable<T>>(
    val default: T,
    val min: T,
    val max: T
) {
    init {
        assert(default in min..max) {
            "Default value ($default) should be within min ($min) .. max ($max) range"
        }
    }

    val range get() = min..max
}
