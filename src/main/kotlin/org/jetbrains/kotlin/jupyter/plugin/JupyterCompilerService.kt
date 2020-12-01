package org.jetbrains.kotlin.jupyter.plugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.jupyter.compiler.JupyterCompiler
import org.jetbrains.kotlin.jupyter.compiler.JupyterScriptClassGetter
import org.jetbrains.kotlin.jupyter.compiler.getSimpleCompiler
import org.jetbrains.kotlin.jupyter.config.ScriptTemplateWithDisplayHelpers
import org.jetbrains.kotlin.jupyter.config.getCompilationConfiguration
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmReplCompilerBase
import org.jetbrains.kotlin.scripting.compiler.plugin.repl.ReplCodeAnalyzerBase
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContext

@Service
class JupyterCompilerService(private val project: Project) {
    init {
        System.setProperty(
            "script.compilation.disable.plugins",
            listOf(
                "org.jetbrains.kotlin.samWithReceiver.SamWithReceiverComponentRegistrar",
                "org.jetbrains.kotlin.noarg.NoArgComponentRegistrar",
                "org.jetbrains.kotlin.android.synthetic.AndroidComponentRegistrar",
                "org.jetbrains.kotlin.allopen.AllOpenComponentRegistrar",
                "org.jetbrains.kotlin.parcelize.ParcelizeComponentRegistrar",
            ).joinToString(";")
        )
        System.setProperty(
            "script.compilation.disable.commandline.processors",
            listOf(
                "org.jetbrains.kotlin.noarg.NoArgCommandLineProcessor",
                "org.jetbrains.kotlin.allopen.AllOpenCommandLineProcessor",
                "org.jetbrains.kotlin.samWithReceiver.SamWithReceiverCommandLineProcessor",
                "org.jetbrains.kotlin.android.synthetic.AndroidCommandLineProcessor",
                // "org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCommandLineProcessor",
            ).joinToString(";")
        )
    }

    private val classesDir: Path by lazy {
        val tempDir = Files.createTempDirectory("kotlin-scripting-jvm-jupyter-kernel")
        tempDir.toFile().deleteOnExit()
        tempDir
    }

    val classWriter = ClassWriter(classesDir)

    private val currentClasspath: List<File> by lazy {
        scriptCompilationClasspathFromContext(
            "notebook-api",
            "compiler",
            classLoader = ScriptTemplateWithDisplayHelpers::class.java.classLoader
        )
    }

    val jupyterCompileConfiguration by lazy {
        getCompilationConfiguration(
            scriptClasspath = currentClasspath + listOf(classesDir.toFile()),
            scriptingClassGetter = JupyterScriptClassGetter {
                jupyterCompiler.previousScriptsClasses
            }
        ) {
            refineConfiguration {
                beforeCompiling { (_, config, _) ->
                    println("Compilation of Jupyter.kts snippet")

                    val previousScriptClasses: Array<KClass<*>> = jupyterCompiler.previousScriptsClasses.toTypedArray()
                    config.with {
                        implicitReceivers(*previousScriptClasses)
                    }.asSuccess()
                }
            }
        }
    }

    val jupyterEvaluationConfiguration by lazy {
        ScriptEvaluationConfiguration {
            jvm {
                baseClassLoader(JupyterDefProvider::class.java.classLoader)
            }
        }
    }

    val jupyterCompiler: JupyterCompiler<KJvmReplCompilerBase<ReplCodeAnalyzerBase>> = getSimpleCompiler(jupyterCompileConfiguration, jupyterEvaluationConfiguration)

    val rwLock = ReentrantLock()
}