// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectModel.kotlin

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.psi.KtClassOrObject

fun GlobalSearchScope.getIndexedTopLevelClassifiers(project: Project): Sequence<KtClassOrObject> {
    return KotlinFullClassNameIndex.getAllElements<KtClassOrObject>(project, this)
}
