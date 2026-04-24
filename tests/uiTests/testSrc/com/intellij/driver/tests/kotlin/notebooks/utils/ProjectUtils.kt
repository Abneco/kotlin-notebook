// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.driver.tests.kotlin.notebooks.utils

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.ProjectManager
import com.intellij.ide.starter.utils.JarUtils
import java.nio.file.Files

internal fun Driver.getProjectPath(): String? = service<ProjectManager>(rdTarget = RdTarget.BACKEND)
    .getOpenProjects()
    .firstOrNull()
    ?.getBasePath()

fun resolveResourceDirectory(resourcePath: String) =
    JarUtils.extractResource(resourcePath.removePrefix("/"),
                             Files.createTempDirectory("ui-test-resource-")
    )
