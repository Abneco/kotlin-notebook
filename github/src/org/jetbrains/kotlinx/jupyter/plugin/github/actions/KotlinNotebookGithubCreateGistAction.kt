// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.github.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.plugins.github.GithubCreateGistAction

/**
 * A wrapper around [GithubCreateGistAction].
 * This action can be used in the Kotlin Notebook toolbar and context menu, and it won't be disabled or hidden when the user
 * does not have a GitHub account connected.
 *
 * This action won't show up in search, otherwise it would be possible to have both this action and
 * [GithubCreateGistAction] in the search results.
 */
class KotlinNotebookGithubCreateGistAction : GithubCreateGistAction() {
    override fun update(e: AnActionEvent) {
        // We're intentionally not calling super.update(e), as it would check for a connected GitHub account,
        //  and we don't want that.

        /** We're hiding this action from search so that we won't show it alongside [GithubCreateGistAction] */
        val fromSearch = ActionPlaces.isMainMenuOrActionSearch(e.place)
        if (fromSearch) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val editor = e.getData(CommonDataKeys.EDITOR)
        val isKotlinNotebook = e.project != null && editor != null && editor.isKotlinNotebook
        e.presentation.isEnabledAndVisible = isKotlinNotebook
    }
}
