// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.util

import org.jetbrains.kotlin.idea.core.script.ucache.scriptsAsEntities

fun <T> switchForScriptsAsEntities(
    on: () -> T,
    off: () -> T,
): T {
    return if (scriptsAsEntities) {
        on()
    } else {
        off()
    }
}
