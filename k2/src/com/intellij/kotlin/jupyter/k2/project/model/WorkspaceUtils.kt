// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.project.model

import com.intellij.kotlin.jupyter.k2.scriptingSupport.KotlinNotebookScriptEntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityBuilder
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId
import org.jetbrains.kotlin.idea.core.script.k2.modules.modifyKotlinScriptLibraryEntity

/**
 * Resolves an existing library entity by [libraryId] and updates it,
 * or creates a new entity with the given [sources] otherwise.
 */
internal fun MutableEntityStorage.addOrUpdateLibraryEntity(
    libraryId: KotlinScriptLibraryEntityId,
    sources: Collection<VirtualFileUrl>,
    usedInScripts: Collection<VirtualFileUrl>,
    updateAction: KotlinScriptLibraryEntityBuilder.() -> Unit = {},
) {
    val existingLibrary = resolve(libraryId)
    if (existingLibrary == null) {
        this addEntity KotlinScriptLibraryEntity(libraryId.classes, usedInScripts.toSet(), KotlinNotebookScriptEntitySource) {
            this.sources += sources
        }
    } else {
        modifyKotlinScriptLibraryEntity(existingLibrary) {
            this.usedInScripts += usedInScripts
            updateAction()
        }
    }
}