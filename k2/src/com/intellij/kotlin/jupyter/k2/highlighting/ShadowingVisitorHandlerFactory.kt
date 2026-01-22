// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.highlighting

import com.intellij.kotlin.jupyter.core.editor.highlighting.visitors.KaDiagnosticData
import com.intellij.kotlin.jupyter.core.editor.highlighting.visitors.KotlinPluginModeShadowingAnalyzerHandler

class ShadowingVisitorHandlerFactory : KotlinPluginModeShadowingAnalyzerHandler.Factory {
    override fun create(): KotlinPluginModeShadowingAnalyzerHandler {
        return K2ShadowingAnalyzerHandler
    }
}

object K2ShadowingAnalyzerHandler : KotlinPluginModeShadowingAnalyzerHandler() {
    override fun applyBeforeProcessingDiagnostic(diagnostic: KaDiagnosticData) {}
}