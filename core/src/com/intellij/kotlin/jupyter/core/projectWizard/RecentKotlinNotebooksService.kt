// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.kotlin.jupyter.core.settings.recents.RecentNotebook
import com.intellij.kotlin.jupyter.core.settings.recents.RecentNotebookWithIcon
import com.intellij.kotlin.jupyter.core.settings.recents.getRecentNotebooks
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.computeFileIconImpl
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

@Service
class RecentKotlinNotebooksService {
    @RequiresBackgroundThread
    fun getNotebooksWithIcons(): List<RecentNotebookWithIcon> {
        return buildSet {
            collectFromSavedState(this)
        }
            .sortedByDescending { it.timeStamp }
            .map { recentNotebook ->
                val icon = computeFileIconImpl(recentNotebook.path, null, 0)
                RecentNotebookWithIcon(recentNotebook, icon)
            }
    }

    private fun collectFromSavedState(result: MutableCollection<RecentNotebook>) {
        result.addAll(
            KotlinNotebookApplicationOptions.getRecentNotebooks()
        )
    }

    companion object {
        fun getInstance(): RecentKotlinNotebooksService = service<RecentKotlinNotebooksService>()
    }
}
