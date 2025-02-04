// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.projectModel

import com.intellij.kotlin.jupyter.core.projectModel.KotlinNotebookSessionLibrariesFilter
import com.intellij.kotlin.jupyter.k2.scriptingSupport.NOTEBOOK_DEPENDENCIES_MODULE_PREFIX
import com.intellij.openapi.roots.libraries.Library

class K2SessionLibrariesFilter : KotlinNotebookSessionLibrariesFilter {
    override fun isInternalLibrary(library: Library): Boolean {
        return library.name?.startsWith(NOTEBOOK_DEPENDENCIES_MODULE_PREFIX) == true
    }
}