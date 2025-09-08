// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.outputs.export

import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

private class SingleFlavorTransferableFactory<DataT : Any>(
    private val myFlavor: DataFlavor,
) {
    private val myFlavors: Array<DataFlavor> = arrayOf(myFlavor)

    fun createTransferable(data: DataT): Transferable {
        return MyTransferable(data)
    }

    private inner class MyTransferable(private val data: DataT) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> {
            return myFlavors
        }

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
            return flavor == myFlavor
        }

        override fun getTransferData(flavor: DataFlavor): Any {
            if (!isDataFlavorSupported(flavor)) {
                throw UnsupportedFlavorException(flavor)
            }
            return data
        }
    }
}

private val ImageTransferableFactory =
    SingleFlavorTransferableFactory<Image>(DataFlavor.imageFlavor)

// We are required to use java.io.File instead of java.nio.Path, because this data flavor requires us to do so
private val FileTransferableFactory =
    SingleFlavorTransferableFactory<List<java.io.File>>(DataFlavor.javaFileListFlavor)

fun createImageDataTransferable(imageData: ByteArray): Transferable {
    val image = ImageIO.read(ByteArrayInputStream(imageData))
    return ImageTransferableFactory.createTransferable(image)
}
