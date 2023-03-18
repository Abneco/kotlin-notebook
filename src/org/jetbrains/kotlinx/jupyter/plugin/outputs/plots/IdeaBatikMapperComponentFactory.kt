// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.outputs.plots

import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.StartupUiUtil
import jetbrains.datalore.vis.svg.SvgSvgElement
import jetbrains.datalore.vis.swing.BatikMapperComponentHelper
import jetbrains.datalore.vis.swing.BatikMapperComponentHelperBase
import jetbrains.datalore.vis.swing.BatikMapperComponentHelperFactory
import jetbrains.datalore.vis.swing.BatikMessageCallback
import org.jetbrains.relocated.apache.batik.ext.awt.RenderingHintsKeyExt
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.lang.ref.WeakReference

class IdeaBatikMapperComponentFactory : BatikMapperComponentHelperFactory {
    override val priority: Int
        get() = 100

    override fun createForUnattached(svgRoot: SvgSvgElement, messageCallback: BatikMessageCallback): BatikMapperComponentHelper {
        require(!svgRoot.isAttached()) { "SvgSvgElement must be unattached" }

        return object: BatikMapperComponentHelperBase(svgRoot, messageCallback) {
            override fun paint(g: Graphics2D) {
                val img = getRenderedImage()
                StartupUiUtil.drawImage(g, img)
            }

            private fun getRenderedImage(): BufferedImage {
                val dimensions = preferredSize
                val width = dimensions.width
                val height = dimensions.height
                val img = ImageUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB)
                val g: Graphics2D = img.createGraphics().apply {
                    setRenderingHint(
                        RenderingHintsKeyExt.KEY_BUFFERED_IMAGE,
                        WeakReference<Any?>(img)
                    )
                    clip(Rectangle(0, 0, width, height))
                }

                myGraphicsNode.paint(g)
                return img
            }

        }
    }
}
