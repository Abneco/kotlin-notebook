// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.sql

import com.intellij.kotlin.jupyter.core.settings.registryFlag

internal val sqlCellsEnabled: Boolean by registryFlag("kotlin.notebook.sqlCells.enabled", false)
