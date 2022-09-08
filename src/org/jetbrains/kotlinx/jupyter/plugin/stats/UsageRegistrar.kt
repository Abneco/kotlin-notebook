// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.stats

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlinx.jupyter.plugin.util.KotlinJupyterResourcesUtil
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object UsageRegistrar {
    private val url = buildString {
        append("https://plugins.jetbrains.com/plugins/list")
        append("?pluginId=${KotlinJupyterResourcesUtil.pluginId}")
        append("&build=${ApplicationInfo.getInstance().build}")
        append("&pluginVersion=${KotlinJupyterResourcesUtil.pluginVersion}")
        append("&os=${URLEncoder.encode(SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION, CharsetToolkit.UTF8)}")
        append("&uuid=${PermanentInstallationID.get()}")
    }

    private const val LAST_REQ_TIME = "kotlin.notebook.lastRequestTime"
    private val UPDATE_PERIOD_MS = TimeUnit.DAYS.toMillis(1)
    private val LOG = logger<UsageRegistrar>()

    private var inProgress: Boolean = false
    private var lastRequestTime: Long
        get() = PropertiesComponent.getInstance().getValue(LAST_REQ_TIME, "0").toLong()
        set(value) {
            PropertiesComponent.getInstance().setValue(LAST_REQ_TIME, value.toString())
        }

    fun pluginWasUsed() {
        ApplicationManager.getApplication().coroutineScope.launch(Dispatchers.Default) {
            val currentTime = System.currentTimeMillis()
            val period = currentTime - lastRequestTime
            val isDevelopment = KotlinJupyterResourcesUtil.isDevVersion

            LOG.debug("Kotlin Notebook was used: period=$period isDevelopment=$isDevelopment inProgress=$inProgress")
            if (!isDevelopment && !inProgress && period > UPDATE_PERIOD_MS) {
                try {
                    inProgress = true
                    LOG.debug("Usage request: run")
                    runRequest()
                    lastRequestTime = currentTime
                    LOG.debug("Usage request: done")
                }
                catch (e: IOException) {
                    LOG.debug("Usage request: failed=$e")
                }
                finally {
                    inProgress = false
                }
            }
        }
    }

    private suspend fun runRequest() {
        withContext(Dispatchers.IO) {
            HttpRequests.request(url).throwStatusCodeException(true).tryConnect()
        }
    }
}
