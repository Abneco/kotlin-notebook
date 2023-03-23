// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.outputs.plots

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.relocated.apache.batik.gvt.GraphicsNode
import org.jetbrains.relocated.apache.batik.gvt.RootGraphicsNode
import org.jetbrains.relocated.apache.batik.gvt.event.GraphicsNodeChangeEvent
import org.jetbrains.relocated.apache.batik.gvt.event.GraphicsNodeChangeListener
import java.awt.image.BufferedImage

@Service
class LetsPlotGraphicsNodesRenderingCache {
    private val cache = CollectionFactory.createConcurrentSoftValueMap<GraphicsNode, BufferedImage>()

    fun getOrCreateImage(node: GraphicsNode, create: () -> BufferedImage): BufferedImage {
        return cache.getOrPut(node) {
            (node as? RootGraphicsNode)?.apply {
                val listener = object : GraphicsNodeChangeListener {
                    override fun changeCompleted(e: GraphicsNodeChangeEvent) {
                        removeTreeGraphicsNodeChangeListener(this)
                        removeCache(this@apply)
                    }
                    override fun changeStarted(e: GraphicsNodeChangeEvent) = Unit
                }

                addTreeGraphicsNodeChangeListener(listener)
            }
            create()
        }
    }

    fun removeCache(node: GraphicsNode) = cache.remove(node)

    companion object {
        fun getInstance() = service<LetsPlotGraphicsNodesRenderingCache>()
    }
}
