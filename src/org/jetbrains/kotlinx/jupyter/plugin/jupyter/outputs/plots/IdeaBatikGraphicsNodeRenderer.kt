// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots

import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.StartupUiUtil
import jetbrains.datalore.vis.swing.BatikGraphicsNodeRenderer
import org.jetbrains.relocated.apache.batik.ext.awt.RenderingHintsKeyExt
import org.jetbrains.relocated.apache.batik.gvt.GraphicsNode
import java.awt.AlphaComposite
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.lang.ref.WeakReference

class IdeaBatikGraphicsNodeRenderer : BatikGraphicsNodeRenderer {
    override val priority: Int
        get() = 100

    override fun paint(node: GraphicsNode, g: Graphics2D, size: Dimension) {
        val img = cacheService.getOrCreateImage(node) { getRenderedImage(node, size) }
        StartupUiUtil.drawImage(g, img)
    }

    private fun getRenderedImage(node: GraphicsNode, size: Dimension): BufferedImage {
        val width = size.width
        val height = size.height
        val img = ImageUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g: Graphics2D = img.createGraphics().apply {
            setRenderingHint(
                RenderingHintsKeyExt.KEY_BUFFERED_IMAGE,
                WeakReference<Any?>(img)
            )
            clip(Rectangle(0, 0, width, height))

            composite = AlphaComposite.SrcOver
            setRenderingHint(RenderingHintsKeyExt.KEY_TRANSCODING, RenderingHintsKeyExt.VALUE_TRANSCODING_VECTOR)
        }

        node.paint(g)
        return img
    }

    companion object {
        private val cacheService get() = LetsPlotGraphicsNodesRenderingCache.getInstance()
    }
}
