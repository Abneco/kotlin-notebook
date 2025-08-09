// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectModel.kotlin

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.psi.KtClassOrObject
import kotlin.script.experimental.api.KotlinType

fun GlobalSearchScope.getIndexedTopLevelClassifiersForTypes(
    project: Project,
    types: Collection<KotlinType>,
): Sequence<KtClassOrObject> {
    val scope = this
    return sequence {
        for (type in types) {
            yieldAll(KotlinFullClassNameIndex[type.typeName, project, scope])
        }
    }
}

fun GlobalSearchScope.getIndexedTopLevelClassifiers(project: Project): Sequence<KtClassOrObject> {
    return KotlinFullClassNameIndex.getAllElements<KtClassOrObject>(project, this)
}