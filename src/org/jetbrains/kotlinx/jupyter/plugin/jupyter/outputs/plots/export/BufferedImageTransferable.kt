// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.export

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage

class BufferedImageTransferable(private val image: BufferedImage) : Transferable {
    override fun getTransferData(flavor: DataFlavor): BufferedImage =
        when (flavor) {
            DataFlavor.imageFlavor -> image
            else -> throw UnsupportedFlavorException(flavor)
        }
    override fun getTransferDataFlavors(): Array<DataFlavor> =
        arrayOf(DataFlavor.imageFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
        transferDataFlavors.contains(flavor)
}
