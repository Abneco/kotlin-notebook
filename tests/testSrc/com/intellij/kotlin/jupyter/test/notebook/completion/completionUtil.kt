// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.kotlin.jupyter.test.LookupFinishMode

fun LookupImpl.finishLookup(
    mode: LookupFinishMode,
    item: LookupElement?,
)= finishLookup(mode.completionChar, item)
