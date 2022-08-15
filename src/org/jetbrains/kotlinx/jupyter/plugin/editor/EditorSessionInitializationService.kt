// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor

import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import kotlinx.coroutines.CompletableDeferred

@Service
class EditorSessionInitializationService {
    private val KEY = Key.create<CompletableDeferred<Unit>>("Session initialization future")

    private fun getOrCreateFuture(editor: Editor): CompletableDeferred<Unit> {
        val originalEditor = (editor as? EditorWindow)?.delegate ?: editor
        return synchronized(originalEditor) {
            originalEditor.getUserData(KEY) ?:
            CompletableDeferred<Unit>().also {
                originalEditor.putUserData(KEY, it)
            }
        }
    }

    fun onSessionInitialized(editor: Editor, action: () -> Unit) {
        getOrCreateFuture(editor).invokeOnCompletion {
            action()
        }
    }

    fun notifySessionInitialized(editor: Editor) {
        getOrCreateFuture(editor).complete(Unit)
    }

    companion object {
        @JvmStatic
        fun getInstance(): EditorSessionInitializationService = service()
    }
}
