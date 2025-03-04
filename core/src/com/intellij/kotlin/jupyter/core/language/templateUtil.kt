// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.language

import com.intellij.kotlin.jupyter.core.settings.registryFlag

val provideTemplatesForCreateActions: Boolean by registryFlag("kotlin.notebook.use.templates.for.create", false)
