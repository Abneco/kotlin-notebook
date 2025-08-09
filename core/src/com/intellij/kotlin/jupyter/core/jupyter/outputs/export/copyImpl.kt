// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.outputs.export

import com.intellij.kotlin.jupyter.core.util.getKotlinNotebookCacheDirectory
import com.intellij.openapi.project.Project
import com.intellij.util.system.OS
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.writeBytes

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

private val FileTransferableFactory =
    SingleFlavorTransferableFactory<List<Path>>(DataFlavor.javaFileListFlavor)

fun createImageDataTransferable(
    project: Project,
    imageData: ByteArray,
    fileName: String,
): Transferable {
    return if (isIntermediateFileWorkaroundNeeded()) {
        val plotFile = project
            .getKotlinNotebookCacheDirectory()
            .resolve("plotExport")
            .resolve(fileName)
        Files.createDirectories(plotFile.parent)
        plotFile.writeBytes(imageData)
        FileTransferableFactory.createTransferable(listOf(plotFile))
    } else {
        val image = ImageIO.read(ByteArrayInputStream(imageData))
        ImageTransferableFactory.createTransferable(image)
    }
}

// Standard copy works incorrectly on Mac, see JBR-6788,
// But it seemingly was fixed in the newer (15 and later) Mac versions
private fun isIntermediateFileWorkaroundNeeded(): Boolean = OS.CURRENT == OS.macOS && !OS.CURRENT.isAtLeast(15, 0)
