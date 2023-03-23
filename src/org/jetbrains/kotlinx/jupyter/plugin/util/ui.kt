// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.util

import javax.swing.UIManager

fun uiFeelsDark(): Boolean? {
    val lafName = UIManager.getLookAndFeel()?.name ?: return null
    return lafName.contains("Darcula") || lafName.contains("dark", true)
}
