// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.statistics.usages

import com.intellij.ide.plugins.StandalonePluginUpdateChecker
import com.intellij.kotlin.jupyter.core.resources.KotlinNotebookResourcesUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import icons.KotlinJupyterIcons

@Service(Service.Level.APP)
internal class KotlinNotebookPluginUpdater : StandalonePluginUpdateChecker(
    KotlinNotebookResourcesUtil.pluginId,
    "kotlin.notebook.lastRequestTime",
    NotificationGroupManager.getInstance().getNotificationGroup("Kotlin Notebook plugin updates"),
    KotlinJupyterIcons.FileIcon,
) {
    override val currentVersion: String
        get() = KotlinNotebookResourcesUtil.pluginVersion

    override fun skipUpdateCheck(): Boolean {
        return KotlinNotebookResourcesUtil.isDevVersion
    }

    companion object {
        fun getInstance(): KotlinNotebookPluginUpdater = service()
    }
}
