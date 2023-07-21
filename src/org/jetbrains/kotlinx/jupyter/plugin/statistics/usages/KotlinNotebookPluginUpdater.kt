// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.statistics.usages

import com.intellij.ide.plugins.StandalonePluginUpdateChecker
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import icons.KotlinJupyterIcons
import org.jetbrains.kotlinx.jupyter.plugin.util.KotlinJupyterResourcesUtil

@Service(Service.Level.APP)
class KotlinNotebookPluginUpdater : StandalonePluginUpdateChecker(
    KotlinJupyterResourcesUtil.pluginId,
    "kotlin.notebook.lastRequestTime",
    NotificationGroupManager.getInstance().getNotificationGroup("Kotlin Notebook plugin updates"),
    KotlinJupyterIcons.FileIcon,
) {
    override val currentVersion: String
        get() = KotlinJupyterResourcesUtil.pluginVersion

    override fun skipUpdateCheck(): Boolean {
        return KotlinJupyterResourcesUtil.isDevVersion
    }

    companion object {
        fun getInstance(): KotlinNotebookPluginUpdater = service()
    }
}
