// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport.definitions

import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.openapi.project.Project

val Project.notebookScriptDefinitionWrapper: KotlinNotebookScriptDefinitionsWrapper
    get() = JupyterCompilerService.getInstance(this).scriptDefinitionsWrapper