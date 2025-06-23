// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard.common

import com.intellij.kotlin.jupyter.core.settings.recents.RecentNotebook
import com.intellij.openapi.actionSystem.DataKey

internal val RECENT_NOTEBOOK_KEY: DataKey<RecentNotebook> = DataKey.create("kotlin.recent.notebook")
internal val NOTEBOOK_TREE_HOLDER_KEY: DataKey<KotlinNotebookTreeHolder> = DataKey.create("kotlin.notebook.tree.holder")
