package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ultimate.PluginVerifier
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlinx.jupyter.compiler.DefaultCompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.config.getCompilationConfiguration
import org.jetbrains.kotlinx.jupyter.plugin.session.KotlinKernelProcessService
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import java.io.File
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm

/**
 * [JupyterCompilerService] stores all compiling-related things across the project:
 * mapping from notebooks to file services and "constant" things equal for all
 * the files.
 *
 * @property project This service project
 */
@Service
class JupyterCompilerService(val project: Project) : Disposable {
    private val mapping: MutableMap<VirtualFile, JupyterCompilerPerFileService> = mutableMapOf()

    init {
        PluginVerifier.verifyUltimatePlugin()
    }

    val initialClasspath: List<File> by lazy {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            KotlinKernelProcessService.getInstance().ideJars
        } else emptyList()
    }

    val initialCompileConfiguration by lazy {
        getCompilationConfiguration(
            scriptClasspath = initialClasspath,
            compilerArgsConfigurator = DefaultCompilerArgsConfigurator(),
        ) {
            refineConfiguration {
                beforeCompiling { (sourceCode, config, _) ->
                    val virtualFile = (sourceCode as? KtFileScriptSource)?.virtualFile
                    val fileDelegate = (virtualFile as? VirtualFileWindow)?.delegate
                    val notebookFile = fileDelegate?.let(BackedNotebookVirtualFile::takeIfBacked) ?: return@beforeCompiling config.asSuccess()
                    getOrCreate(notebookFile).handleBeforeCompiling(sourceCode, config).asSuccess()
                }
            }
        }
    }

    val evaluationConfiguration by lazy {
        ScriptEvaluationConfiguration {
            jvm {
                baseClassLoader(this@JupyterCompilerService::class.java.classLoader)
            }
        }
    }

    val scriptDefinition by lazy {
        ScriptDefinition(
            initialCompileConfiguration,
            evaluationConfiguration
        )
    }

    val fileExtension: String by lazy {
        initialCompileConfiguration[ScriptCompilationConfiguration.fileExtension] ?: "jupyter.kts"
    }

    val fileSuffix: String by lazy {
        ".$fileExtension"
    }

    val language = Language.findLanguageByID("kotlin")!!

    fun getOrCreate(virtualFile: BackedNotebookVirtualFile): JupyterCompilerPerFileService {
        return mapping.getOrPut(virtualFile.file) { JupyterCompilerPerFileService(virtualFile, this) }
    }

    fun get(virtualFile: BackedNotebookVirtualFile): JupyterCompilerPerFileService? {
        return mapping[virtualFile.file]
    }

    override fun dispose() {
    }

    companion object {
        fun getInstance(project: Project) = project.service<JupyterCompilerService>()

        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): JupyterCompilerPerFileService {
            return getInstance(project).getOrCreate(virtualFile)
        }
    }
}
