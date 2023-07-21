// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots

import jetbrains.datalore.vis.swing.GraphicsNodeInitializer
import org.jetbrains.relocated.apache.batik.gvt.GraphicsNode
import org.jetbrains.relocated.apache.batik.gvt.RootGraphicsNode
import org.jetbrains.relocated.apache.batik.gvt.event.GraphicsNodeChangeEvent
import org.jetbrains.relocated.apache.batik.gvt.event.GraphicsNodeChangeListener

class IdeaGraphicsNodeInitializer : GraphicsNodeInitializer {
    override fun initialize(node: GraphicsNode) {
        (node as? RootGraphicsNode)?.apply {
            val listener = object : GraphicsNodeChangeListener {
                override fun changeCompleted(e: GraphicsNodeChangeEvent) {
                    LetsPlotGraphicsNodesRenderingCache.getInstance().removeCache(this@apply)
                }
                override fun changeStarted(e: GraphicsNodeChangeEvent) = Unit
            }

            addTreeGraphicsNodeChangeListener(listener)
        }
    }
}
