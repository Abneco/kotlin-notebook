package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.jetbrains.python.sdk.isNotEmptyDirectory
import java.io.File

object KernelSpecDetector {
    private val log = Logger.getInstance(this::class.java)

    private const val JUPYTER_KERNELS_PATH = "jupyter/kernels"

    private val userHome by lazy {
        File(System.getProperty("user.home").orEmpty())
    }

    private const val sysPrefix = "usr/local"

    private fun env(name: String): String {
        return System.getenv(name).orEmpty()
    }

    fun getKernelDir(kernelName: String): File? {
        val kernelsDir = getKernelsDir {
            it.isDirectory && it.resolve(kernelName).isDirectory
        }
        return kernelsDir?.resolve(kernelName)
    }

    fun getKernelsDir(filter: (File) -> Boolean = { it.exists() && it.isNotEmptyDirectory }): File? {
        val dirs = getPossibleDirs()
        val kernelsDir = dirs.firstOrNull(filter)

        if (kernelsDir == null) {
            log.warn("Jupyter kernel specs were not found on your computer.")
            log.warn("Searched in:")
            dirs.forEach { file ->
                log.warn("> ${file.absolutePath}")
            }
        }

        return kernelsDir
    }

    fun getPossibleDirs(): List<File> {
        return when {
            SystemInfo.isWindows -> windowsPaths()
            SystemInfo.isMac -> macosPaths()
            SystemInfo.isLinux -> linuxPaths()
            else -> nixPaths()
        }
    }

    private fun windowsPaths(): List<File> {
        return listOf(
            File(env("APPDATA")),
            File(sysPrefix),
            File(env("PROGRAMDATA"))
        ).map {
            it.resolve(JUPYTER_KERNELS_PATH)
        }
    }

    private fun linuxPaths(): List<File> {
        return listOf(userHome.resolve(".local/share/$JUPYTER_KERNELS_PATH")) + nixPaths()
    }

    private fun macosPaths(): List<File> {
        return listOf(userHome.resolve("Library/Jupyter/kernels")) + nixPaths()
    }

    private fun nixPaths(): List<File> {
        return listOf(
            File(sysPrefix).resolve("share"),
            File("/usr/local/share"),
            File("/usr/share"),
        ).map {
            it.resolve(JUPYTER_KERNELS_PATH)
        }
    }
}
