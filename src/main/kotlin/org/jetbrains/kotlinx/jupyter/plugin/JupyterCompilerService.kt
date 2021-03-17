package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.isFile
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlinx.jupyter.compiler.DefaultCompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.config.getCompilationConfiguration
import org.jetbrains.plugins.notebooks.core.impl.file.NotebookVirtualFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.streams.toList

@Service
class JupyterCompilerService(private val project: Project) {
    private val mapping: MutableMap<VirtualFile, JupyterCompilerPerFileService> = mutableMapOf()

    private val kotlinKernelPath = run {
        // resolve paths correctly: https://jupyter-client.readthedocs.io/en/stable/kernels.html#kernel-specs
        Paths.get("C:/Users/Ilya.Muradyan/AppData/Roaming/jupyter/kernels/kotlin/jars")
    }

    val initialClasspath: List<File> = run {
        Files.walk(kotlinKernelPath).filter { path ->
            path.isFile() && !path.fileName.toString().contains("kotlin-jupyter-kernel")
        }.map {
            it.toFile()
        }.toList()
    }

    val initialCompileConfiguration = run {
        getCompilationConfiguration(
            scriptClasspath = initialClasspath,
            compilerArgsConfigurator = DefaultCompilerArgsConfigurator(),
        ) {
            refineConfiguration {
                beforeCompiling { (sourceCode, config, _) ->
                    val virtualFile = (sourceCode as? KtFileScriptSource)?.virtualFile
                    val fileDelegate = (virtualFile as? VirtualFileWindow)?.delegate
                    val notebookFile = fileDelegate as? NotebookVirtualFile ?: return@beforeCompiling config.asSuccess()
                    get(notebookFile).handleBeforeCompiling(sourceCode, config).asSuccess()
                }
            }
        }
    }

    val evaluationConfiguration =
        ScriptEvaluationConfiguration {
            jvm {
                baseClassLoader(this@JupyterCompilerService::class.java.classLoader)
            }
        }

    val fileExtension = run {
        initialCompileConfiguration[ScriptCompilationConfiguration.fileExtension] ?: "jupyter-kts"
    }

    fun get(virtualFile: NotebookVirtualFile): JupyterCompilerPerFileService {
        return mapping.getOrPut(virtualFile, { JupyterCompilerPerFileService(project, virtualFile, this) })
    }

    companion object {
        fun getInstance(project: Project) = project.service<JupyterCompilerService>()

        fun getForFile(project: Project, virtualFile: NotebookVirtualFile): JupyterCompilerPerFileService {
            return getInstance(project).get(virtualFile)
        }
    }
}
