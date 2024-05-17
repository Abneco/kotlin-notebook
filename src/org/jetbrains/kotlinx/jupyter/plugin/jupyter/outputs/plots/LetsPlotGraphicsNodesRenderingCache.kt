// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.relocated.apache.batik.gvt.GraphicsNode
import java.awt.image.BufferedImage

@Service
class LetsPlotGraphicsNodesRenderingCache {
    private val cache = CollectionFactory.createConcurrentSoftValueMap<GraphicsNode, BufferedImage>()

    fun getOrCreateImage(node: GraphicsNode, create: () -> BufferedImage): BufferedImage {
        val image = cache.getOrPut(node, create)
        if (cache.size > CACHE_LIMIT) cache.clear()
        return image
    }

    fun removeCache(node: GraphicsNode) = cache.remove(node)

    companion object {
        private const val CACHE_LIMIT = 10

        fun getInstance() = service<LetsPlotGraphicsNodesRenderingCache>()
    }
}
