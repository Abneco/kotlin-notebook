// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.driver.tests.kotlin.notebooks.plugins

/**
 * Storage of plugin IDs used in `additionalPlugins` across UI tests.
 */
object IdePluginIds {
  const val AI_ASSISTANT = "com.intellij.ml.llm"

  // Android
  const val ANDROID = "org.jetbrains.android"
  const val ANDROID_DESIGN = "com.android.tools.design"
  const val ANDROIDX_COMPOSE_IDEA = "androidx.compose.plugins.idea"

  // Native/Debugger
  const val NATIVE_DEBUG = "com.intellij.nativeDebug"

  // Media/Formats
  const val WEBP = "intellij.webp"

  // Kotlin Multiplatform
  const val KOTLIN_MPP = "com.jetbrains.kmm"

  // Kotlin DataFrame (used in notebooks tests)
  const val DATAFRAME = "intellij.kotlin.dataframe.plugin"
}
