// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport.fir

import org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPlugins
import java.nio.file.Path

/**
 * Available options of IDEA-bundled compiler plugins to be used in the notebook.
 * These plugins would affect the analysis resolution and code insight visible to the user.
 *
 * @property bundledJarLocation Location of the actual bundled jar file which will be used during [FirSession] setup.
 * @property pluginEnvironmentOptions Additional compiler options for the plugin, if applicable.
 */
enum class NotebookAnalysisBundledCompilerPlugins(
    val visibleName: String,
    val bundledJarLocation: Path,
    val pluginEnvironmentOptions: List<String> = emptyList(),
) {
    DATA_FRAME(
        "DataFrame",
        KotlinK2BundledCompilerPlugins.DATAFRAME_COMPILER_PLUGIN.bundledJarLocation,
        buildList {
            add("-Xwarning-level")
            add("EXPOSED_PROPERTY_TYPE:disabled")
        }
    ),

    COMPOSE(
        "Compose",
        KotlinK2BundledCompilerPlugins.COMPOSE_COMPILER_PLUGIN.bundledJarLocation,
    );
}