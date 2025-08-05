// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import com.intellij.openapi.application.PathManager.getSystemDir
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCachePath
import java.nio.file.Path

private const val KOTLIN_NOTEBOOK_CACHE_DIR_NAME = "kotlinNotebook"

fun Project.getKotlinNotebookCacheDirectory(): Path = getProjectCachePath(KOTLIN_NOTEBOOK_CACHE_DIR_NAME)

fun getKotlinNotebookAppCacheDirectory(): Path = getSystemDir().resolve(KOTLIN_NOTEBOOK_CACHE_DIR_NAME)
