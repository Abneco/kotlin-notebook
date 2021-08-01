package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.kotlinx.jupyter.plugin.util.isNotEmptyDirectory
import java.io.File

/**
 * This utility object detects installed Jupyter kernels.
 * In case of Kotlin kernel we need JAR files distributed with kernel
 * to add them to the script classpath.
 */
object KernelSpecDetector {
    private val LOG = Logger.getInstance(KernelSpecDetector::class.java)

    private const val JUPYTER_KERNELS_PATH = "jupyter/kernels"

    private val userHome by lazy {
        File(System.getProperty("user.home").orEmpty())
    }

    private const val sysPrefix = "usr/local"

    private fun env(name: String): String {
        return System.getenv(name).orEmpty()
    }

    /**
     * Find the kernel directory by kernel name
     *
     * @param kernelName Name of the kernel how it is returned
     * by `kernel_info_reply` in `language_info.name`
     * field ([documentation](https://jupyter-client.readthedocs.io/en/stable/messaging.html#kernel-info)).
     * Should be `kotlin` for Kotlin kernel.
     *
     * @return Found directory or `null` if it was not found.
     */
    fun getKernelDir(kernelName: String): File? {
        val kernelsDir = getKernelsDir {
            it.isDirectory && it.resolve(kernelName).isDirectory
        }
        return kernelsDir?.resolve(kernelName)
    }

    /**
     * Returns first found directory with kernels which satisfies the given [filter].
     *
     * Respects [kernel detection order](https://jupyter-client.readthedocs.io/en/stable/kernels.html#kernel-specs).
     */
    fun getKernelsDir(filter: (File) -> Boolean = { it.exists() && it.isNotEmptyDirectory }): File? {
        val dirs = getPossibleDirs()
        val kernelsDir = dirs.firstOrNull(filter)

        if (kernelsDir == null) {
            LOG.warn("Jupyter kernel specs were not found on your computer.")
            LOG.warn("Searched in:")
            dirs.forEach { file ->
                LOG.warn("> ${file.absolutePath}")
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
