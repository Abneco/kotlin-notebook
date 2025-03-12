// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.kotlin.jupyter.core.settings.recents.RecentNotebook
import com.intellij.kotlin.jupyter.core.settings.recents.getRecentNotebooks
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service
class RecentKotlinNotebooksService {
    fun getNotebooks(): List<RecentNotebook> {
        return buildSet {
            collectFromSavedState(this)
        }.sortedByDescending { it.timeStamp }
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
