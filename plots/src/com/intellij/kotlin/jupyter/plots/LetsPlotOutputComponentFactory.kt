// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots

import com.intellij.jupyter.core.jupyter.editor.outputs.createExecutionCountHolder
import com.intellij.jupyter.core.jupyter.editor.outputs.updateExecutionCountHolder
import com.intellij.jupyter.core.jupyter.helper.notebookFileOrNull
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.notebooks.visualization.outputs.NotebookOutputComponentFactory
import com.intellij.notebooks.visualization.outputs.NotebookOutputComponentFactory.Companion.executionCountHolder
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.letsPlot.batik.plot.util.ServiceLoaderHelper

class LetsPlotOutputComponentFactory: NotebookOutputComponentFactory<LetsPlotComponent, LetsPlotOutputDataKey> {
    override val componentClass: Class<LetsPlotComponent>
        get() = LetsPlotComponent::class.java
    override val outputDataKeyClass: Class<LetsPlotOutputDataKey>
        get() = LetsPlotOutputDataKey::class.java

    override fun createComponent(
        editor: EditorImpl,
        outputDataKey: LetsPlotOutputDataKey
    ): NotebookOutputComponentFactory.CreatedComponent<LetsPlotComponent> {
        ServiceLoaderHelper.addClassLoader(LetsPlotOutputComponentFactory::class.java.classLoader)
        val component = LetsPlotComponent()
        component.initialize(outputDataKey)
        return NotebookOutputComponentFactory.CreatedComponent(
            component,
            NotebookOutputComponentFactory.WidthStretching.STRETCH_AND_SQUEEZE,
            limitHeight = false,
            resizable = true,
            { "LetsPlot plot" },
            outputDataKey.createExecutionCountHolder(),
            null
        )
    }

    override fun updateComponent(editor: EditorImpl, component: LetsPlotComponent, outputDataKey: LetsPlotOutputDataKey) {
        LOG.trace("Updating existing component inside: ${editor.notebookFileOrNull?.file?.name}")
        outputDataKey.updateExecutionCountHolder(component.executionCountHolder)
        component.initialize(outputDataKey)
    }

    override fun match(component: LetsPlotComponent, outputDataKey: LetsPlotOutputDataKey): NotebookOutputComponentFactory.Match {
        return if (component.dataKey == outputDataKey) {
            NotebookOutputComponentFactory.Match.SAME
        } else {
            NotebookOutputComponentFactory.Match.NONE
        }
    }

    companion object {
        private val LOG = notebookLogger()
    }
}