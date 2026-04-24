// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.driver.tests.kotlin.notebooks.base

import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.screenRecorder.IDEScreenRecorder
import com.intellij.tools.ide.util.common.replaceSpecialCharactersWithHyphens
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class IDEScreenRecordingExtension(private val runContext: () -> IDERunContext?) : BeforeEachCallback, AfterEachCallback {
  private var recorder: IDEScreenRecorder? = null

  override fun beforeEach(context: ExtensionContext) {
    val rc = runContext() ?: return
    val recordingDir = rc.logsDir.resolve("screenRecording")
    val fileName = context.displayName.replaceSpecialCharactersWithHyphens()

    recorder = IDEScreenRecorder.create(rc, recordingDir, fileName)
    recorder?.start()
  }

  override fun afterEach(context: ExtensionContext) {
    recorder?.stop()
    recorder = null
  }
}
