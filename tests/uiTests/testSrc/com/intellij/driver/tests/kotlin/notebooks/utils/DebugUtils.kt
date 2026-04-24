package com.intellij.driver.tests.kotlin.notebooks.utils

import com.intellij.driver.client.Driver
import com.intellij.jupyter.ui.test.util.utils.setRegistry

internal const val k2DebugFeaturesRegistryKey = "kotlin.notebook.debug.enabled"
internal const val k2DebugDebugCellActionRegistryKey = "kotlin.notebook.debug.cell.action.enabled"

internal fun Driver.setNotebookDebugFeatures(value: Boolean) {
  setRegistry(k2DebugFeaturesRegistryKey, value)
  setRegistry(k2DebugDebugCellActionRegistryKey, value)
}

