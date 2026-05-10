// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.driver.tests.kotlin.notebooks.utils

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.invokeActionWithRetries
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.ui.components.notebooks.NotebookEditorUiComponent
import com.intellij.driver.sdk.ui.components.notebooks.notebookEditor
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForOne
import kotlin.time.Duration.Companion.seconds

fun Driver.createNewNotebook(name: String = "New Notebook") {
  ideFrame {
    leftToolWindowToolbar.projectButton.open() // making sure the project view is open and in focus for correct scrolling
    projectView {
      projectViewTree.run {
        waitFor("wait for project tree to load", 30.seconds) {
          getAllTexts().isNotEmpty()
        }
          invokeActionWithRetries("ScrollPane-scrollHome") // making sure the first line is within the visible bounds
          getAllTexts().first().strictClick()
      }
    }

    invokeAction("NewKotlinNotebookAction", false)

    popup().apply {
      textField { byAccessibleName("Name") }.apply {
        strictClick()
        text = name
      }

      keyboard { enter() } // submit the popup

      waitFor("new notebook popup should close", 15.seconds) {
        notPresent()
      }
    }

    waitFor("the editor is present") {
      notebookEditor().present()
    }
  }
}

fun NotebookEditorUiComponent.checkCreateNewNotebook(name: String = "test") {
    step("Create a new notebook") {
        driver.createNewNotebook(name)
    }
    step("Check the notebook editor is opened") {
        waitForOne(
            "Waiting for the notebook editor to appear",
            timeout = 10.seconds,
            getter = { notebookCellEditors }
        )
    }
}