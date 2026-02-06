// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

/**
 * Strategy used when checking highlight information
 */
enum class HighlightCheckStrategy {
    OnlyValidSyntax, // No errors should be found in the highlighting info
    WithErrors, // Errors should be found in the highlighting info
    ShadowedErrors // There should be errors in cells not in focus, but the cell in focus has valid syntax.
}
