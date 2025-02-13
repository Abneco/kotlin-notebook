// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots.export

import com.intellij.kotlin.jupyter.plots.LetsPlotOutputDataKey

class SaveAllPlotsAction : SavePlotAction() {
    override fun supportsMultiplePlots(): Boolean = true

    override fun isActionApplicable(outputs: List<LetsPlotOutputDataKey>): Boolean {
        return super.isActionApplicable(outputs) && outputs.size > 1
    }
}
