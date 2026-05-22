// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.premium

import com.intellij.jupyter.core.premium.JupyterFearureAvailabilityInfoProvider
import com.intellij.jupyter.core.premium.JupyterFeatureAvailabilityContext
import com.intellij.jupyter.core.premium.JupyterFeatureAvailabilityCustomizer
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook

class KotlinJupyterFeaturesAvailabilityCustomizer : JupyterFearureAvailabilityInfoProvider.AllFeaturesAvailable(), JupyterFeatureAvailabilityCustomizer {
    override fun isApplicable(context: JupyterFeatureAvailabilityContext): Boolean {
        return context.notebookFile.isKotlinNotebook
    }
}
