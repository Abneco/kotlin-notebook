package org.jetbrains.kotlin.jupyter.plugin

import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.nio.file.Path
import kotlin.script.experimental.jvm.impl.KJvmCompiledModuleInMemory
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript

class ClassWriter(private val outputDir: Path) {
    fun writeCompiledSnippet(snippet: KJvmCompiledScript) {
        val moduleInMemory = snippet.getCompiledModule() as KJvmCompiledModuleInMemory
        moduleInMemory.compilerOutputFiles.forEach { (name, bytes) ->
            if (name.endsWith(".class")) {
                writeClass(bytes, outputDir.resolve(name))
            }
        }
    }

    private fun writeClass(classBytes: ByteArray, path: Path) {
        FileOutputStream(path.toAbsolutePath().toString()).use { fos ->
            BufferedOutputStream(fos).use { out ->
                out.write(classBytes)
                out.flush()
            }
        }
    }
}
