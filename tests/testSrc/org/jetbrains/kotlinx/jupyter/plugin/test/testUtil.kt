// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.test

import com.intellij.openapi.application.PathManager
import org.jetbrains.plugins.notebooks.JupyterCommonRule
import org.jetbrains.plugins.notebooks.jupyter.JupyterBaseTestCase
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

val baseTestDataPath = PathManager.getHomePath() + "/plugins/kotlin/jupyter/tests/testData"

@RunWith(JUnit4::class)
abstract class KotlinNotebookBaseTestCase : JupyterBaseTestCase() {
    @JvmField
    @Rule
    val kotlinNotebookCommonRule = JupyterCommonRule(
        withClearPasswordSafe = false,
        withProductionDataManagerRule = false,
        withClearJupyterSettings = true
    )
}
