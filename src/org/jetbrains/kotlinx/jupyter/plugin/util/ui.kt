// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.util.ui.StartupUiUtil

fun uiFeelsDark(): Boolean? {
    return StartupUiUtil.isDarkTheme
}
